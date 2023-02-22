package izumi.distage.testkit.runner

import distage.{Activation, BootstrapModule, DIKey, Injector, Module, PlannerInput, TagK}
import izumi.distage.config.model.AppConfig
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.framework.services.ModuleProvider
import izumi.distage.model.definition.Binding.SetElementBinding
import izumi.distage.model.definition.ImplDef
import izumi.distage.model.definition.errors.DIError
import izumi.distage.model.plan.{ExecutableOp, Plan}
import izumi.distage.modules.DefaultModule
import izumi.distage.modules.support.IdentitySupportModule
import izumi.distage.roles.launcher.LogConfigLoader.LogConfigLoaderImpl
import izumi.distage.roles.launcher.{ActivationParser, CLILoggerOptions, RoleAppActivationParser, RouterFactory}
import izumi.distage.testkit.model.TestEnvironment.EnvExecutionParams
import izumi.distage.testkit.model.{DistageTest, TestActivationStrategy, TestEnvironment}
import izumi.distage.testkit.runner.TestPlanner.*
import izumi.distage.testkit.runner.services.{TestConfigLoader, TestkitLogging}
import izumi.functional.IzEither.*
import izumi.functional.quasi.{QuasiAsync, QuasiIO, QuasiIORunner}
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.fundamentals.platform.functional.Identity
import izumi.logstage.api.IzLogger
import izumi.logstage.api.logger.LogRouter

import scala.util.Try

object TestPlanner {

  final case class MemoizationEnv(
    envExec: EnvExecutionParams,
    integrationLogger: IzLogger,
    runtimePlan: Plan,
    memoizationInjector: Injector[Identity],
    highestDebugOutputInTests: Boolean,
  )

  final case class PreparedTest[F[_]](
    test: DistageTest[F],
    appModule: Module,
    testPlan: Plan,
    activation: Activation,
  )

  final case class EnvMergeCriteria(
    bsPlanMinusActivations: Vector[ExecutableOp],
    bsModuleMinusActivations: BootstrapModule,
    runtimePlan: Plan,
  )

  final case class PackedEnv[F[_]](
    envMergeCriteria: EnvMergeCriteria,
    preparedTests: Seq[PreparedTest[F]],
    memoizationPlanTree: List[Plan],
    anyMemoizationInjector: Injector[Identity],
    anyIntegrationLogger: IzLogger,
    highestDebugOutputInTests: Boolean,
    strengthenedKeys: Set[DIKey],
  )

  sealed trait PlanningFailure
  object PlanningFailure {
    case class Exception(throwable: Throwable) extends PlanningFailure
    case class DIErrors(errors: List[DIError]) extends PlanningFailure
  }

  final case class PlannedTests[F[_]](
    good: Map[MemoizationEnv, Seq[MemoizationTree[F]]], // in fact there should always be just one element
    bad: Seq[(Seq[DistageTest[F]], PlanningFailure)],
  )
}

class TestPlanner[F[_]: TagK: DefaultModule](
  logging: TestkitLogging,
  configLoader: TestConfigLoader,
) {
  // first we need to plan runtime for our monad. Identity is also supported
  val runtimeGcRoots: Set[DIKey] = Set(
    DIKey.get[QuasiIORunner[F]],
    DIKey.get[QuasiIO[F]],
    DIKey.get[QuasiAsync[F]],
  )
  /**
    * Performs tests grouping by it's memoization environment.
    * [[TestEnvironment.EnvExecutionParams]] - contains only parts of environment that will not affect plan.
    * Grouping by such structure will allow us to create memoization groups with shared logger and parallel execution policy.
    * By result you'll got [[TestEnvironment.MemoizationEnv]] mapped to [[MemoizationTree]] - tree-represented memoization plan with tests.
    * [[TestEnvironment.MemoizationEnv]] represents memoization environment, with shared [[Injector]], and runtime plan.
    */
  def groupTests(distageTests: Seq[DistageTest[F]]): PlannedTests[F] = {
    val out: Seq[(Map[MemoizationEnv, MemoizationTree[F]], Seq[(Seq[DistageTest[F]], PlanningFailure)])] = distageTests
      .groupBy(_.environment.getExecParams)
      .view
      .mapValues(_.groupBy(_.environment))
      .toSeq
      .map {
        case (envExec, testsByEnv) =>
          val configLoadLogger = IzLogger(envExec.logLevel).withCustomContext("phase" -> "testRunner")

          val memoizationEnvs =
            QuasiAsync[Identity].parTraverse(testsByEnv) {
              case (env, tests) =>
                // make a config loader for current env with logger
                val config = configLoader.loadConfig(env, configLoadLogger)

                // test loggers will not create polling threads and will log immediately
                val logConfigLoader = new LogConfigLoaderImpl(CLILoggerOptions(envExec.logLevel, json = false), configLoadLogger)
                val logConfig = logConfigLoader.loadLoggingConfig(config)
                val (router, _) = new RouterFactory.RouterFactoryImpl().createRouter(logConfig)(identity)

                prepareGroupPlans(envExec, config, env, tests, router).left.map(bad => (tests, bad))
            }

          val good = memoizationEnvs.collect {
            case Right(env) =>
              env
          }

          // merge environments together by equality of their shared & runtime plans
          // in a lot of cases memoization plan will be the same even with many minor changes to TestConfig,
          // so this saves a lot of reallocation of memoized resources
          val goodTrees: Map[MemoizationEnv, MemoizationTree[F]] = good.groupBy(_.envMergeCriteria).map {
            case (EnvMergeCriteria(_, _, runtimePlan), packedEnv) =>
              val integrationLogger = packedEnv.head.anyIntegrationLogger
              val memoizationInjector = packedEnv.head.anyMemoizationInjector
              val highestDebugOutputInTests = packedEnv.exists(_.highestDebugOutputInTests)
              val memoizationTree = MemoizationTree[F](packedEnv)
              val env = MemoizationEnv(envExec, integrationLogger, runtimePlan, memoizationInjector, highestDebugOutputInTests)
              (env, memoizationTree)
          }

          val bad: Seq[(Seq[DistageTest[F]], PlanningFailure)] = memoizationEnvs.collect {
            case Left((tests, problem)) =>
              ((tests, problem))
          }

          (goodTrees, bad)
      }

    val good = out.flatMap(_._1.toSeq).groupBy(_._1).view.mapValues(_.map(_._2)).toMap
    val bad = out.flatMap(_._2)

    PlannedTests(good, bad)
  }

  private lazy val allowedKeyVariations = {
    // FIXME: HACK: _bootstrap_ keys that may vary between envs but shouldn't cause them to differ (because they should only impact bootstrap)
    val activationKeys = Set(DIKey[Activation]("bootstrapActivation"), DIKey[ActivationInfo])
    val recursiveKeys = Set(DIKey[BootstrapModule])
    // FIXME: remove IzLogger dependency in `ResourceRewriter` and stop inserting LogstageModule in bootstrap
    val hackyKeys = Set(DIKey[LogRouter])
    activationKeys ++ recursiveKeys ++ hackyKeys
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

      prepareTestEnv(env, tests, lateLogger, fullActivation, moduleProvider).left.map(errors => PlanningFailure.DIErrors(errors))
    }.toEither.left.map(PlanningFailure.Exception).flatten
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
          config,
          env.activationInfo,
          env.activation,
          Activation.empty,
          lateLogger,
          warnUnset,
        )
        val configActivation = activationParser.parseActivation()

        configActivation ++ env.activation
    }
  }

  private[this] def prepareTestEnv(
    env: TestEnvironment,
    tests: Seq[DistageTest[F]],
    lateLogger: IzLogger,
    fullActivation: Activation,
    moduleProvider: ModuleProvider,
  ): Either[List[DIError], PackedEnv[F]] = {
    val bsModule = moduleProvider.bootstrapModules().merge overriddenBy env.bsModule
    val appModule = {
      // add default module manually, instead of passing it to Injector, to be able to split it later into runtime/non-runtime manually
      IdentitySupportModule ++ DefaultModule[F] overriddenBy
      moduleProvider.appModules().merge overriddenBy env.appModule
    }

    val (bsPlanMinusVariableKeys, bsModuleMinusVariableKeys, injector) = {
      // FIXME: Including both bootstrap Plan & bootstrap Module into merge criteria to prevent `Bootloader`
      //  becoming inconsistent across envs (if BootstrapModule isn't considered it could come from different env than expected).

      val injector = Injector[Identity](bootstrapActivation = fullActivation, overrides = Seq(bsModule))

      val injectorEnv = injector.providedEnvironment

      val variableBsKeys = allowedKeyVariations
      val bsPlanMinusVariableKeys = injectorEnv.bootstrapLocator.plan.stepsUnordered.filterNot(variableBsKeys contains _.target).toVector
      val bsModuleMinusVariableKeys = injectorEnv.bootstrapModule.drop(variableBsKeys)

      (bsPlanMinusVariableKeys, bsModuleMinusVariableKeys, injector)
    }

    for {
      // runtime plan with `runtimeGcRoots`
      runtimePlan <- injector.plan(PlannerInput(appModule, fullActivation, runtimeGcRoots))
      // all keys created in runtimePlan, we filter them out later to not recreate any components already in runtimeLocator
      runtimeKeys = runtimePlan.keys

      // produce plan for each test
      testPlans <- tests.map {
        distageTest =>
          val forcedRoots = env.forcedRoots.getActiveKeys(fullActivation)
          val testRoots = distageTest.test.get.diKeys.toSet ++ forcedRoots
          for {
            plan <- if (testRoots.nonEmpty) injector.plan(PlannerInput(appModule, fullActivation, testRoots)) else Right(Plan.empty)
          } yield {
            PreparedTest(distageTest, appModule, plan, fullActivation)
          }
      }.biAggregate
      envKeys = testPlans.flatMap(_.testPlan.keys).toSet

      // we need to "strengthen" all _memoized_ weak set instances that occur in our tests to ensure that they
      // be created and persist in memoized set. we do not use strengthened bindings afterwards, so non-memoized
      // weak sets behave as usual
      (strengthenedKeys, strengthenedAppModule) = appModule.drop(runtimeKeys).foldLeftWith(List.empty[DIKey]) {
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
                    plan <- prepareSharedPlan(envKeys, runtimeKeys, levelRoots, fullActivation, injector, levelModule)
                  } yield {
                    ((acc ++ List(plan), allSharedKeys ++ plan.keys))
                  }
                } else {
                  Right((acc, allSharedKeys))
                }
            }.map(_._1)
        } else {
          prepareSharedPlan(envKeys, runtimeKeys, Set.empty, fullActivation, injector, strengthenedAppModule).map(p => List(p))
        }
    } yield {
      val envMergeCriteria = EnvMergeCriteria(bsPlanMinusVariableKeys, bsModuleMinusVariableKeys, runtimePlan)

      val memoEnvHashCode = envMergeCriteria.hashCode()
      val integrationLogger = lateLogger("memoEnv" -> memoEnvHashCode)
      if (strengthenedKeys.nonEmpty) {
        integrationLogger.log(logging.testkitDebugMessagesLogLevel(env.debugOutput))(
          s"Strengthened weak components: $strengthenedKeys"
        )
      }

      val highestDebugOutputInTests = tests.exists(_.environment.debugOutput)
      PackedEnv(envMergeCriteria, testPlans, orderedPlans, injector, integrationLogger, highestDebugOutputInTests, strengthenedKeys.toSet)
    }
  }

  private[this] def prepareSharedPlan(
    envKeys: Set[DIKey],
    runtimeKeys: Set[DIKey],
    memoizationRoots: Set[DIKey],
    activation: Activation,
    injector: Injector[Identity],
    appModule: Module,
  ): Either[List[DIError], Plan] = {
    val sharedKeys = envKeys.intersect(memoizationRoots) -- runtimeKeys
    if (sharedKeys.nonEmpty) {
      injector.plan(PlannerInput(appModule, activation, sharedKeys))
    } else {
      Right(Plan.empty)
    }
  }
}
