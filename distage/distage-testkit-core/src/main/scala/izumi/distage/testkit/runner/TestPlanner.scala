package izumi.distage.testkit.runner

import distage.{Activation, BootstrapModule, DIKey, Injector, Module, PlannerInput, TagK}
import izumi.distage.config.model.AppConfig
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.model.definition.Binding.SetElementBinding
import izumi.distage.model.definition.ImplDef
import izumi.distage.model.definition.errors.DIError
import izumi.distage.model.plan.{ExecutableOp, Plan}
import izumi.distage.modules.DefaultModule
import izumi.distage.modules.support.IdentitySupportModule
import izumi.distage.roles.launcher.{ActivationParser, CLILoggerOptions, RoleAppActivationParser}
import izumi.distage.testkit.model.TestEnvironment.EnvExecutionParams
import izumi.distage.testkit.model.{DistageTest, TestActivationStrategy, TestEnvironment}
import izumi.distage.testkit.runner.TestPlanner.*
import izumi.distage.testkit.runner.services.{BootstrapFactory, LateLoggerFactoryCachingImpl, ReporterBracket, TestkitLogging}
import izumi.functional.quasi.{QuasiAsync, QuasiIO, QuasiIORunner}
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.fundamentals.platform.functional.Identity
import izumi.logstage.api.IzLogger
import izumi.logstage.api.logger.LogRouter
import izumi.functional.IzEither.*

import java.util.concurrent.ConcurrentHashMap

class TestPlanner[F[_]: TagK: DefaultModule](
  reporterBracket: ReporterBracket[F],
  logging: TestkitLogging,
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
  def groupTests(distageTests: Seq[DistageTest[F]], loggerCache: LateLoggerFactoryCachingImpl.Cache): Either[List[DIError], Map[MemoizationEnv, MemoizationTree[F]]] = {

    // FIXME: HACK: _bootstrap_ keys that may vary between envs but shouldn't cause them to differ (because they should only impact bootstrap)
    val allowVariationKeys = {
      val activationKeys = Set(DIKey[Activation]("bootstrapActivation"), DIKey[ActivationInfo])
      val recursiveKeys = Set(DIKey[BootstrapModule])
      // FIXME: remove IzLogger dependency in `ResourceRewriter` and stop inserting LogstageModule in bootstrap
      val hackyKeys = Set(DIKey[LogRouter])
      activationKeys ++ recursiveKeys ++ hackyKeys
    }

    // here we are grouping our tests by memoization env

    distageTests
      .groupBy(_.environment.getExecParams).map {
        case (envExec, grouped) =>
          val configLoadLogger = IzLogger(envExec.logLevel).withCustomContext("phase" -> "testRunner")
          val memoizationEnvs = QuasiAsync.quasiAsyncIdentity
            .parTraverse(grouped.groupBy(_.environment)) {
              case (env, tests) =>
                reporterBracket.withRecoverFromFailedExecution(tests) {
                  Option(prepareGroupPlans(loggerCache, allowVariationKeys, envExec, configLoadLogger, env, tests))
                }(None)
            }.flatten.biAggregate
          // merge environments together by equality of their shared & runtime plans
          // in a lot of cases memoization plan will be the same even with many minor changes to TestConfig,
          // so this saves a lot of reallocation of memoized resources

          for {
            envs <- memoizationEnvs
          } yield {
            envs.groupBy(_.envMergeCriteria).map {
              case (EnvMergeCriteria(_, _, runtimePlan), packedEnv) =>
                val integrationLogger = packedEnv.head.anyIntegrationLogger
                val memoizationInjector = packedEnv.head.anyMemoizationInjector
                val highestDebugOutputInTests = packedEnv.exists(_.highestDebugOutputInTests)
                val memoizationTree = MemoizationTree[F](packedEnv)
                MemoizationEnv(envExec, integrationLogger, runtimePlan, memoizationInjector, highestDebugOutputInTests) -> memoizationTree
            }
          }
      }.biAggregate.map(_.flatten.toMap)
  }

  private def prepareGroupPlans(
    loggerCache: LateLoggerFactoryCachingImpl.Cache,
    variableBsKeys: Set[DIKey],
    envExec: EnvExecutionParams,
    configLoadLogger: IzLogger,
    env: TestEnvironment,
    tests: Seq[DistageTest[F]],
  ): Either[List[DIError], PackedEnv[F]] = {
    // make a config loader for current env with logger
    val config = loadConfig(env, configLoadLogger)

    withLeakedLogger(envExec, loggerCache, config, configLoadLogger) {
      router =>
        val lateLogger = IzLogger(router)

        val fullActivation = env.activationStrategy match {
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

        // here we scan our classpath to enumerate of our components (we have "bootstrap" components - injector plugins, and app components)
        val moduleProvider =
          env.bootstrapFactory.makeModuleProvider[F](envExec.planningOptions, config, lateLogger.router, env.roles, env.activationInfo, fullActivation)

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

          val bsPlanMinusVariableKeys = injectorEnv.bootstrapLocator.plan.stepsUnordered.filterNot(variableBsKeys contains _.target)
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
          val envMergeCriteria = EnvMergeCriteria(bsPlanMinusVariableKeys.toVector, bsModuleMinusVariableKeys, runtimePlan)

          val memoEnvHashCode = envMergeCriteria.hashCode()
          val integrationLogger = lateLogger("memoEnv" -> memoEnvHashCode)
          val highestDebugOutputInTests = tests.exists(_.environment.debugOutput)
          if (strengthenedKeys.nonEmpty) {
            integrationLogger.log(logging.testkitDebugMessagesLogLevel(env.debugOutput))(
              s"Strengthened weak components: $strengthenedKeys"
            )
          }

          PackedEnv(envMergeCriteria, testPlans, orderedPlans, injector, integrationLogger, highestDebugOutputInTests, strengthenedKeys.toSet)
        }

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

  private def withLeakedLogger[A](
    envExec: EnvExecutionParams,
    loggerCache: LateLoggerFactoryCachingImpl.Cache,
    config: AppConfig,
    earlyLogger: IzLogger,
  )(withRouter: LogRouter => A
  ) = {
    import scala.jdk.CollectionConverters.*

    new LateLoggerFactoryCachingImpl(
      config,
      CLILoggerOptions(envExec.logLevel, json = false),
      earlyLogger,
      loggerCache,
    ).makeLateLogRouter {
      closeables =>
        loggerCache.closeables.addAll(closeables.asJava)
        ()
    }.use(logger => withRouter(logger.router))
  }

  protected[this] def loadConfig(env: TestEnvironment, envLogger: IzLogger): AppConfig = {
    TestPlanner.memoizedConfig
      .computeIfAbsent(
        (env.configBaseName, env.bootstrapFactory, env.configOverrides),
        _ => {
          val configLoader = env.bootstrapFactory
            .makeConfigLoader(env.configBaseName, envLogger)
            .map {
              appConfig =>
                env.configOverrides match {
                  case Some(overrides) =>
                    AppConfig(overrides.config.withFallback(appConfig.config).resolve())
                  case None =>
                    appConfig
                }
            }
          configLoader.loadConfig()
        },
      )
  }

}

object TestPlanner {
  private final val memoizedConfig = new ConcurrentHashMap[(String, BootstrapFactory, Option[AppConfig]), AppConfig]

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
}
