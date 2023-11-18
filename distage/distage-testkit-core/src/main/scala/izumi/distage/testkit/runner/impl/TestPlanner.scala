package izumi.distage.testkit.runner.impl

import distage.{Activation, BootstrapModule, DIKey, Injector, LocatorRef, Module, Planner, PlannerInput, TagK}
import izumi.distage.bootstrap.BootstrapLocator
import izumi.distage.config.model.AppConfig
import izumi.distage.framework.services.{ModuleProvider, PlanCircularDependencyCheck}
import izumi.distage.model.definition.Binding.SetElementBinding
import izumi.distage.model.definition.ImplDef
import izumi.distage.model.definition.errors.DIError
import izumi.distage.model.plan.{ExecutableOp, Plan}
import izumi.distage.modules.DefaultModule
import izumi.distage.modules.support.IdentitySupportModule
import izumi.distage.roles.launcher.LogConfigLoader.LogConfigLoaderImpl
import izumi.distage.roles.launcher.{ActivationParser, CLILoggerOptions, RoleAppActivationParser, RouterFactory}
import izumi.distage.testkit.model.TestEnvironment.EnvExecutionParams
import izumi.distage.testkit.model.{DistageTest, TestActivationStrategy, TestEnvironment, TestTree}
import izumi.distage.testkit.runner.impl.TestPlanner.*
import izumi.distage.testkit.runner.impl.services.{TestConfigLoader, TestkitLogging}
import izumi.distage.testkit.spec.DistageTestEnv
import izumi.functional.IzEither.*
import izumi.functional.quasi.QuasiIO.syntax.*
import izumi.functional.quasi.{QuasiAsync, QuasiIO, QuasiIORunner}
import izumi.fundamentals.collections.nonempty.NEList
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.fundamentals.platform.functional.Identity
import izumi.logstage.api.IzLogger
import izumi.logstage.api.logger.{LogQueue, LogRouter}

import scala.annotation.nowarn
import scala.util.Try

object TestPlanner {

  final case class PackedEnv[F[_]](
    envMergeCriteria: PackedEnvMergeCriteria,
    preparedTests: Seq[AlmostPreparedTest[F]],
    memoizationPlanTree: List[Plan],
    envInjector: Injector[Identity],
    highestDebugOutputInTests: Boolean,
    strengthenedKeys: Set[DIKey],
  )
  final case class AlmostPreparedTest[F[_]](
    test: DistageTest[F],
    appModule: Module,
    targetKeys: Set[DIKey],
    activation: Activation,
  )

  final case class InjectorEquivalenceCriteria(
    bsPlanMinusActivations: Vector[ExecutableOp],
    bsModuleMinusActivations: BootstrapModule,
  )

  final case class PackedEnvMergeCriteria(
    injectorEquivalenceCriteria: InjectorEquivalenceCriteria,
    runtimePlan: Plan,
  )

  final case class PreparedTestEnv(
    envExec: EnvExecutionParams,
    runtimePlan: Plan,
    memoizationInjector: Injector[Identity],
    highestDebugOutputInTests: Boolean,
  )

  sealed trait PlanningFailure
  object PlanningFailure {
    final case class Exception(throwable: Throwable) extends PlanningFailure
    final case class DIErrors(errors: NEList[DIError]) extends PlanningFailure
  }

  final case class PlannedTestEnvs[F[_]](envs: Map[PreparedTestEnv, TestTree[F]])
  final case class PlannedTests[F[_]](
    good: Seq[PlannedTestEnvs[F]], // in fact there should always be just one element
    bad: Seq[(Seq[DistageTest[F]], PlanningFailure)],
  )
}

class TestPlanner[F[_]: TagK: DefaultModule](
  logging: TestkitLogging,
  configLoader: TestConfigLoader,
  testTreeBuilder: TestTreeBuilder[F],
  testRunnerLocator: LocatorRef,
  logBuffer: LogQueue,
) {
  // first we need to plan runtime for our monad, which is retained by TestTreeRunner. Identity is also supported.
  private val runtimeGcRoots: Set[DIKey] = Set(
    DIKey.get[QuasiIORunner[F]],
    DIKey.get[TestTreeRunner[F]],
  )
  /**
    * Performs tests grouping by it's memoization environment.
    * [[TestEnvironment.EnvExecutionParams]] - contains only parts of environment that will not affect plan.
    * Grouping by such structure will allow us to create memoization groups with shared logger and parallel execution policy.
    * By result you'll got [[PackedEnv]] mapped to [[izumi.distage.testkit.runner.impl.TestTreeBuilder.TestTreeBuilderImpl.MemoizationTreeBuilder]]
    * - tree-represented memoization plan with tests.
    * [[PackedEnv]] represents memoization environment, with shared [[Injector]], and runtime plan.
    */
  @nowarn("msg=Unused import")
  def groupTests[G[_]](distageTests: Seq[DistageTest[F]])(implicit G: QuasiIO[G], GA: QuasiAsync[G]): G[PlannedTests[F]] = {
    import scala.collection.compat.*

    for {
      out <- G.traverse(
        distageTests
          .groupBy(_.environment.getExecParams)
          .view
          .mapValues(_.groupBy(_.environment))
          .toSeq
      ) {
        case (envExec, testsByEnv) =>
          val configLoadLogger = IzLogger(envExec.logLevel).withCustomContext("phase" -> "testRunner")

          for {
            memoizationEnvs <- GA.parTraverse(testsByEnv) {
              case (env, tests) =>
                G.maybeSuspend {
                  // make a config loader for current env with logger
                  val config = configLoader.loadConfig(env, configLoadLogger)

                  // test loggers will not create polling threads and will log immediately
                  val logConfigLoader = new LogConfigLoaderImpl(CLILoggerOptions(envExec.logLevel, json = false), configLoadLogger)
                  val logConfig = logConfigLoader.loadLoggingConfig(config)
                  val router = new RouterFactory.RouterFactoryImpl().createRouter(logConfig, logBuffer)

                  prepareGroupPlans(envExec, config, env, tests, router).left.map(bad => (tests, bad))
                }
            }
          } yield {

            val good = memoizationEnvs
              .collect {
                case Right(env) =>
                  env
              }
              .filter(_.preparedTests.nonEmpty)

            // merge environments together by equality of their shared & runtime plans
            // in a lot of cases memoization plan will be the same even with many minor changes to TestConfig,
            // so this saves a lot of reallocation of memoized resources
            val goodTrees: Map[PreparedTestEnv, TestTree[F]] = good.groupBy(_.envMergeCriteria).map {
              case (criteria, packedEnv) =>
                // injectors do NOT provide equality but we defined custom injector equvalence for the purpose
                // any injector from the group would do
                val memoizationInjector = packedEnv.head.envInjector
                val runtimePlan = criteria.runtimePlan
                assert(runtimeGcRoots.diff(runtimePlan.keys).isEmpty)

                val memoizationTree = testTreeBuilder.build(memoizationInjector, runtimePlan, packedEnv)

                val highestDebugOutputInTests = packedEnv.exists(_.highestDebugOutputInTests)
                val env = PreparedTestEnv(envExec, runtimePlan, memoizationInjector, highestDebugOutputInTests)
                (env, memoizationTree)
            }

            val bad: Seq[(Seq[DistageTest[F]], PlanningFailure)] = memoizationEnvs.collect {
              case Left((tests, problem)) =>
                (tests, problem)
            }

            (PlannedTestEnvs(goodTrees), bad)
          }
      }
    } yield {
      val good = out.map(_._1)
      val bad = out.flatMap(_._2)

      PlannedTests(good, bad)
    }
  }

  // FIXME: this shit is too fragile, this needs to be solved properly
  private lazy val allowedKeyVariations: Set[DIKey] = {
    // FIXME: remove IzLogger dependency in `ResourceRewriter` and stop inserting LogstageModule in bootstrap
    val hackyKeys = Set(DIKey[LogRouter])
    // FIXME: HACK: _bootstrap_ keys that may vary between envs but shouldn't cause them to differ (because they should only impact bootstrap)
    BootstrapLocator.selfReflectionKeys ++
    // test runtime adds more informative bootstrap keys:
    DistageTestEnv.testkitBootstrapReflectiveKeys ++
    hackyKeys
  }

  private def prepareGroupPlans(
    envExec: EnvExecutionParams,
    config: AppConfig,
    env: TestEnvironment,
    tests: Seq[DistageTest[F]],
    router: LogRouter,
  ): Either[PlanningFailure, PackedEnv[F]] = {
    Try {
      val lateLogger = IzLogger(router)

      val fullActivation = makeTestActivation(config, env, lateLogger)

      // here we scan our classpath to enumerate of our components (we have "bootstrap" components - injector plugins, and app components)
      val moduleProvider =
        env.bootstrapFactory.makeModuleProvider[F](envExec.planningOptions, config, router, env.roles, env.activationInfo, fullActivation)

      prepareTestEnv(envExec, env, tests, lateLogger, fullActivation, moduleProvider).left.map(errors => PlanningFailure.DIErrors(errors): PlanningFailure)
    }.toEither.left.map(e => PlanningFailure.Exception(e): PlanningFailure).flatMap(identity)
  }

  private def makeTestActivation(config: AppConfig, env: TestEnvironment, lateLogger: IzLogger): Activation = {
    env.activationStrategy match {
      case TestActivationStrategy.IgnoreConfig =>
        env.activation
      case TestActivationStrategy.LoadConfig(ignoreUnknown, warnUnset) =>
        val roleAppActivationParser = new RoleAppActivationParser.Impl(
          logger = lateLogger,
          ignoreUnknownActivations = ignoreUnknown,
        )
        val activationParser = new ActivationParser.Impl(
          roleAppActivationParser,
          RawAppArgs.empty,
          env.activationInfo,
          env.activation,
          Activation.empty,
          lateLogger,
          warnUnset,
        )
        val configActivation = activationParser.parseActivation(config)

        configActivation ++ env.activation
    }
  }

  private[this] def prepareTestEnv(
    envExecutionParams: EnvExecutionParams,
    env: TestEnvironment,
    tests: Seq[DistageTest[F]],
    lateLogger: IzLogger,
    fullActivation: Activation,
    moduleProvider: ModuleProvider,
  ): Either[NEList[DIError], PackedEnv[F]] = {
    val bsModule = moduleProvider.bootstrapModules().merge overriddenBy env.bsModule
    val appModule = {
      // add default module manually, instead of passing it to Injector, to be able to split it later into runtime/non-runtime manually
      IdentitySupportModule ++ DefaultModule[F] overriddenBy
      moduleProvider.appModules().merge overriddenBy env.appModule
    }

    val (injectorEquivalence, injector) = {
      // FIXME: Including both bootstrap Plan & bootstrap Module into merge criteria to prevent `Bootloader`
      //  becoming inconsistent across envs (if BootstrapModule isn't considered it could come from different env than expected).

      val injector = Injector[Identity](
        // here we reuse all the components from test runner locator which are required as dependencies for IndividualTestRunner
        parent = Some(testRunnerLocator.get),
        bootstrapActivation = fullActivation,
        overrides = Seq(bsModule),
      )

      val injectorEnv = injector.providedEnvironment

      val variableBsKeys = allowedKeyVariations
      val bsPlanMinusVariableKeys = injectorEnv.bootstrapLocator.plan.stepsUnordered.filterNot(variableBsKeys contains _.target).toVector
      val bsModuleMinusVariableKeys = injectorEnv.bootstrapModule.drop(variableBsKeys)

      (InjectorEquivalenceCriteria(bsPlanMinusVariableKeys, bsModuleMinusVariableKeys), injector)
    }

    for {
      planChecker <- Right(new PlanCircularDependencyCheck(envExecutionParams.planningOptions, lateLogger))

      // runtime plan with `runtimeGcRoots`
      runtimePlan <- injector.plan(
        PlannerInput(
          appModule ++ new TestRuntimeModule(envExecutionParams),
          fullActivation,
          runtimeGcRoots,
        )
      )
      _ <- Right(planChecker.showProxyWarnings(runtimePlan))
      // all keys created in runtimePlan, we filter them out later to not recreate any components already in runtimeLocator
      runtimeKeys = runtimePlan.keys
      // this is not critical, TestTreeBuilder excludes the keys anyway
      reducedAppModule = appModule.drop(runtimeKeys)

      // produce plan for each test
      testPlans <- tests
        .groupBy {
          distageTest =>
            val forcedRoots = env.forcedRoots.getActiveKeys(fullActivation)
            val testRoots = forcedRoots ++ distageTest.test.get.diKeys
            testRoots
        }
        .toSeq
        .map {
          case (testRoots, distageTests) =>
            for {
              plan <- if (testRoots.nonEmpty) injector.plan(PlannerInput(reducedAppModule, fullActivation, testRoots)) else Right(Plan.empty)
              _ <- Right(planChecker.showProxyWarnings(plan))
            } yield {
              distageTests.map(AlmostPreparedTest(_, reducedAppModule, plan.keys, fullActivation))
            }
        }.biFlatten
      envKeys = testPlans.flatMap(_.targetKeys).toSet

      // we need to "strengthen" all _memoized_ weak set instances that occur in our tests to ensure that they
      // be created and persist in memoized set. we do not use strengthened bindings afterwards, so non-memoized
      // weak sets behave as usual
      (strengthenedKeys, strengthenedAppModule) = reducedAppModule.foldLeftWith(List.empty[DIKey]) {
        case (acc, b @ SetElementBinding(key, r: ImplDef.ReferenceImpl, _, _)) if r.weak && (envKeys(key) || envKeys(r.key)) =>
          (key :: acc) -> b.copy(implementation = r.copy(weak = false))
        case (acc, b) =>
          acc -> b
      }

      orderedPlans <-
        if (env.memoizationRoots.keys.nonEmpty) {
          // we need to create plans for each level of memoization
          // every duplicated key will be removed
          // every empty memoization level (after keys filtering) will be removed

          env.memoizationRoots.keys.toList
            .sortBy(_._1)
            .biFoldLeft((List.empty[Plan], Set.empty[DIKey])) {
              case ((acc, allSharedKeys), (_, keys)) =>
                val levelRoots = envKeys.intersect(keys.getActiveKeys(fullActivation) -- allSharedKeys)
                val levelModule = strengthenedAppModule.drop(allSharedKeys)
                if (levelRoots.nonEmpty) {
                  for {
                    plan <- prepareSharedPlan(envKeys, runtimeKeys, levelRoots, fullActivation, injector, levelModule, planChecker)
                  } yield {
                    ((acc ++ List(plan), allSharedKeys ++ plan.keys))
                  }
                } else {
                  Right((acc, allSharedKeys))
                }
            }.map(_._1)
        } else {
          prepareSharedPlan(envKeys, runtimeKeys, Set.empty, fullActivation, injector, strengthenedAppModule, planChecker).map(p => List(p))
        }
    } yield {
      val envMergeCriteria = PackedEnvMergeCriteria(injectorEquivalence, runtimePlan)

      if (strengthenedKeys.nonEmpty) {
        lateLogger.log(logging.testkitDebugMessagesLogLevel(env.debugOutput))(
          s"Strengthened weak components: $strengthenedKeys"
        )
      }

      val highestDebugOutputInTests = tests.exists(_.environment.debugOutput)
      PackedEnv(envMergeCriteria, testPlans, orderedPlans, injector, highestDebugOutputInTests, strengthenedKeys.toSet)
    }
  }

  private[this] def prepareSharedPlan(
    envKeys: Set[DIKey],
    runtimeKeys: Set[DIKey],
    memoizationRoots: Set[DIKey],
    activation: Activation,
    injector: Planner,
    appModule: Module,
    check: PlanCircularDependencyCheck,
  ): Either[NEList[DIError], Plan] = {
    val sharedKeys = envKeys.intersect(memoizationRoots) -- runtimeKeys

    for {
      plan <-
        if (sharedKeys.nonEmpty) {
          injector.plan(PlannerInput(appModule, activation, sharedKeys))
        } else {
          Right(Plan.empty)
        }
      _ <- Right(check.showProxyWarnings(plan))
    } yield {
      plan
    }

  }
}
