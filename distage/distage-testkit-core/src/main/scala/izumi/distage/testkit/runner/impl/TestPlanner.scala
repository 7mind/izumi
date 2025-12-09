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
import izumi.distage.testkit.model.TestConfig.Parallelism
import izumi.distage.testkit.model.TestEnvironment.EnvExecutionParams
import izumi.distage.testkit.model.{DistageTest, TestActivationStrategy, TestEnvironment, TestTree}
import izumi.distage.testkit.runner.impl.TestPlanner.*
import izumi.distage.testkit.runner.impl.services.{ParTraverseExt, TestConfigLoader, TestkitLogging}
import izumi.distage.testkit.spec.DistageTestEnv
import izumi.functional.IzEither.*
import izumi.functional.quasi.QuasiIO.syntax.*
import izumi.functional.quasi.{QuasiIO, QuasiIORunner}
import izumi.fundamentals.collections.nonempty.NEList
import izumi.fundamentals.platform.cli.model.RoleAppArgs
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.types.HigherKindedAny.AnyF
import izumi.logstage.api.IzLogger
import izumi.logstage.api.logger.{LogQueue, LogRouter}

import scala.annotation.nowarn
import scala.annotation.unchecked.uncheckedVariance
import scala.util.Try

object TestPlanner {

  final case class PackedEnv[+F[_]](
    envMergeCriteria: PackedEnvMergeCriteria,
    preparedTests: Seq[AlmostPreparedTest[F]],
    memoizationPlanTree: List[Plan],
    envInjector: Injector[Identity],
    highestDebugOutputInTests: Boolean,
    strengthenedKeys: Set[DIKey.SetElementKey],
  )
  final case class AlmostPreparedTest[+F[_]](
    test: DistageTest[F],
    appModule: Module,
    targetKeys: Set[DIKey],
    activation: Activation,
  )

  final case class InjectorEquivalenceCriteria(
    bsPlanMinusActivations: Set[ExecutableOp],
    bsModuleMinusActivations: BootstrapModule,
  )

  final case class PackedEnvMergeCriteria(
    injectorEquivalenceCriteria: InjectorEquivalenceCriteria,
    runtimePlanCriteria: Plan,
  )

  final case class PreparedTestEnv[F[_]](
    envExec: EnvExecutionParams.Aux[F],
    runtimePlan: Plan,
    runtimeInjector: Injector[Identity],
    highestDebugOutputInTests: Boolean,
  )

  sealed trait PlanningFailure
  object PlanningFailure {
    final case class Exception(throwable: Throwable) extends PlanningFailure
    final case class DIErrors(errors: NEList[DIError]) extends PlanningFailure
  }

  final case class PlannedTestEnvs[+F[_]](
    envs: Map[PreparedTestEnv[F], TestTree[F]] @uncheckedVariance
  )
  final case class PlannedTests[F[_]](
    good: Seq[PlannedTestEnvs[F]],
    bad: Seq[(Seq[DistageTest[F]], PlanningFailure)],
  )
}

@nowarn("msg=[Uu]nused import")
class TestPlanner(
  logging: TestkitLogging,
  configLoader: TestConfigLoader,
  testTreeBuilder: TestTreeBuilder,
  testRunnerLocator: LocatorRef,
  logBuffer: LogQueue,
) {
  import scala.collection.compat.*

  /**
    * Group tests by their memoization environment.
    * [[TestEnvironment.EnvExecutionParams]] - contains parts of environment that may radically affect planning.
    * Grouping by such structure will allow us to create memoization groups with shared logger and parallel execution policy.
    * @return [[PackedEnv]] mapped to [[izumi.distage.testkit.runner.impl.TestTreeBuilder.TestTreeBuilderImpl.MemoizationTreeBuilder]]
    * - tree-represented memoization plan with tests.
    * [[PackedEnv]] represents memoization environment, with shared [[Injector]], and runtime plan.
    */
  def planGroupTests[F[_]](distageTests: Seq[DistageTest[AnyF]], parTraverseExt: ParTraverseExt[F])(implicit F: QuasiIO[F]): F[PlannedTests[AnyF]] = {

    for {
      out <- F.traverse(
        distageTests
          .groupBy(_.environment.getExecParams)
          .view
          .mapValues(_.groupBy(_.environment))
          .toSeq
      ) {
        case (envExec, testsByEnv) =>
          planTestEnvs[F, envExec.F](envExec, testsByEnv, parTraverseExt)
      }
    } yield {
      val good = out.map(_._1)
      val bad = out.flatMap(_._2)

      PlannedTests(good, bad)
    }
  }

  private def planTestEnvs[F[_], TestF[_]](
    envExec: EnvExecutionParams.Aux[TestF],
    testsByEnv: Map[TestEnvironment, Seq[DistageTest[AnyF]]],
    parTraverseExt: ParTraverseExt[F],
  )(implicit
    F: QuasiIO[F]
  ): F[(PlannedTestEnvs[AnyF], List[(Seq[DistageTest[AnyF]], PlanningFailure)])] = {
    import envExec.{effectType, defaultModule}

    // first we need to plan runtime for our monad, which is retained by TestTreeRunner. Identity is also supported.
    val runtimeGcRoots: Set[DIKey] = Set(
      DIKey.get[QuasiIORunner[TestF]],
      DIKey.get[TestTreeRunner[TestF]],
    )

    val configLoadLogger = IzLogger(envExec.logLevel).withCustomContext("phase" -> "testRunner")

    for {
      memoizationEnvs <- parTraverseExt.configuredParTraverse(Parallelism.Unlimited)(testsByEnv) {
        case (env, tests) =>
          F.maybeSuspend {

            // make a config loader for current env with logger
            val config = configLoader.loadConfig(env, configLoadLogger)

            // test loggers will not create polling threads and will log immediately
            val logConfigLoader = new LogConfigLoaderImpl(CLILoggerOptions(envExec.logLevel, json = false), configLoadLogger)
            val logConfig = logConfigLoader.loadLoggingConfig(config)
            val router = new RouterFactory.RouterFactoryConsoleSinkImpl().createRouter(logConfig, logBuffer)

            prepareGroupPlans[TestF](envExec, config, env, tests.asInstanceOf[Seq[DistageTest[TestF]]], router, runtimeGcRoots)(using effectType, defaultModule).left.map(
              failure => (tests, failure)
            )
          }
      }
    } yield {
      val (bad, good0) = memoizationEnvs.partitionMap(identity)
      val good = good0.filter(_.preparedTests.nonEmpty)

      // merge environments together by equality of their shared & runtime plans
      // in a lot of cases memoization plan will be the same even with many minor changes to TestConfig,
      // so this saves a lot of reallocation of memoized resources
      val envsGroupedByPlanEquality = good.groupBy(_.envMergeCriteria)
      val goodTrees: Map[PreparedTestEnv[TestF], TestTree[TestF]] = envsGroupedByPlanEquality.map {
        case (mergeCriteria, packedEnvs) =>
          // injectors do NOT provide equality, but we defined custom injector equivalence for the purpose
          // any injector from the group would do
          val memoizationInjector = packedEnvs.head.envInjector
          val runtimePlan = mergeCriteria.runtimePlanCriteria
          assert((runtimeGcRoots -- runtimePlan.keys).isEmpty)

          val memoizationTree = testTreeBuilder.build(memoizationInjector, runtimePlan, packedEnvs)

          val highestDebugOutputInTests = packedEnvs.exists(_.highestDebugOutputInTests)
          val env = PreparedTestEnv[TestF](envExec, runtimePlan, memoizationInjector, highestDebugOutputInTests)
          (env, memoizationTree)
      }

      (PlannedTestEnvs(goodTrees), bad)
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

  private def prepareGroupPlans[TestF[_]: TagK: DefaultModule](
    envExec: EnvExecutionParams,
    config: AppConfig,
    env: TestEnvironment,
    tests: Seq[DistageTest[TestF]],
    router: LogRouter,
    runtimeGcRoots: Set[DIKey],
  ): Either[PlanningFailure, PackedEnv[TestF]] = {
    Try {
      val lateLogger = IzLogger(router)

      val fullActivation = makeTestActivation(config, env, lateLogger)

      // here we scan our classpath to enumerate of our components (we have "bootstrap" components - injector plugins, and app components)
      val moduleProvider =
        env.bootstrapFactory.makeModuleProvider[TestF](envExec.planningOptions, config, router, env.roles, env.activationInfo, fullActivation)

      prepareTestEnv(envExec, env, tests, lateLogger, fullActivation, moduleProvider, runtimeGcRoots).left.map(errors => PlanningFailure.DIErrors(errors))
    }.toEither.left.map(e => PlanningFailure.Exception(e)).flatMap(identity)
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
          RoleAppArgs.empty,
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

  private def prepareTestEnv[F[_]: TagK: DefaultModule](
    envExecutionParams: EnvExecutionParams,
    env: TestEnvironment,
    tests: Seq[DistageTest[F]],
    lateLogger: IzLogger,
    fullActivation: Activation,
    moduleProvider: ModuleProvider,
    runtimeGcRoots: Set[DIKey],
  ): Either[NEList[DIError], PackedEnv[F]] = {
    val bsModule = moduleProvider.bootstrapModules().merge overriddenBy env.bsModule
    val appModule = {
      // add default module manually, instead of passing it to Injector, to be able to split it later into runtime/non-runtime manually
      IdentitySupportModule ++ DefaultModule[F] overriddenBy
      moduleProvider.appModules().merge overriddenBy env.appModule
    }

    val (injectorEquivalence, envInjector) = {
      // FIXME: Including both bootstrap Plan & bootstrap Module into merge criteria to prevent `Bootloader`
      //  becoming inconsistent across envs (if BootstrapModule isn't considered it could come from different env than expected).

      val injector = Injector[Identity](
        // here we reuse all the components from test runner locator which are required as dependencies for IndividualTestRunner
        parent = Some(testRunnerLocator.get),
        bootstrapActivation = fullActivation,
        bootstrapOverrides = Seq(bsModule),
      )

      val injectorEnv = injector.providedEnvironment

      val variableBsKeys = allowedKeyVariations
      val bsPlanMinusVariableKeys = injectorEnv.bootstrapLocator.plan.stepsUnordered.filterNot(variableBsKeys contains _.target).toSet
      val bsModuleMinusVariableKeys = injectorEnv.bootstrapModule.drop(variableBsKeys)

      (InjectorEquivalenceCriteria(bsPlanMinusVariableKeys, bsModuleMinusVariableKeys), injector)
    }

    for {
      planChecker <- Right(new PlanCircularDependencyCheck(envExecutionParams.planningOptions, lateLogger))

      // runtime plan with `runtimeGcRoots`
      runtimePlan <- envInjector.plan(
        PlannerInput(
          appModule ++ new TestRuntimeModule[F](envExecutionParams),
          runtimeGcRoots,
          fullActivation,
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
              plan <- if (testRoots.nonEmpty) envInjector.plan(PlannerInput(reducedAppModule, testRoots, fullActivation)) else Right(Plan.empty)
              _ <- Right(planChecker.showProxyWarnings(plan))
            } yield {
              distageTests.map(AlmostPreparedTest(_, reducedAppModule, plan.keys, fullActivation))
            }
        }.biFlatten
      envKeys = testPlans.flatMap(_.targetKeys).toSet

      // we need to "strengthen" all _memoized_ weak set instances that occur in our tests to ensure that they
      // be created and persist in memoized set. we do not use strengthened bindings afterwards, so non-memoized
      // weak sets behave as usual.
      // NOTE: there's no check for memoization here. However, there is in TestTreeBuilder: we filter out non-memoized elements
      // to not accidentally strengthen unmemoized keys.
      (strengthenedKeys, strengthenedAppModule) = reducedAppModule.foldLeftWith(Set.empty[DIKey.SetElementKey]) {
        case (acc, b @ SetElementBinding(key, r: ImplDef.ReferenceImpl, _, _)) if r.weak && (envKeys(key) || envKeys(r.key)) =>
          (acc + key) -> b.copy(implementation = r.copy(weak = false))
        case (acc, b) =>
          acc -> b
      }

      memoizationPlanTree <-
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
                    plan <- prepareSharedPlan(envKeys, runtimeKeys, levelRoots, fullActivation, envInjector, levelModule, planChecker)
                  } yield {
                    (acc ++ List(plan), allSharedKeys ++ plan.keys)
                  }
                } else {
                  Right((acc, allSharedKeys))
                }
            }.map(_._1)
        } else {
          prepareSharedPlan(envKeys, runtimeKeys, Set.empty, fullActivation, envInjector, strengthenedAppModule, planChecker).map(p => List(p))
        }
    } yield {
      val envMergeCriteria = PackedEnvMergeCriteria(injectorEquivalence, runtimePlan)

      if (strengthenedKeys.nonEmpty) {
        lateLogger.log(logging.testkitDebugMessagesLogLevel(env.debugOutput))(
          s"Strengthened weak components: $strengthenedKeys"
        )
      }

      val highestDebugOutputInTests = tests.exists(_.environment.debugOutput)
      PackedEnv(envMergeCriteria, testPlans, memoizationPlanTree, envInjector, highestDebugOutputInTests, strengthenedKeys)
    }
  }

  private def prepareSharedPlan(
    envKeys: Set[DIKey],
    runtimeKeys: Set[DIKey],
    memoizationRoots: Set[DIKey],
    activation: Activation,
    injector: Planner,
    appModule: Module,
    planChecker: PlanCircularDependencyCheck,
  ): Either[NEList[DIError], Plan] = {
    val sharedKeys = envKeys.intersect(memoizationRoots) -- runtimeKeys

    for {
      plan <- if (sharedKeys.nonEmpty) injector.plan(PlannerInput(appModule, sharedKeys, activation)) else Right(Plan.empty)
      _ <- Right(planChecker.showProxyWarnings(plan))
    } yield {
      plan
    }

  }
}
