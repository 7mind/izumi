package izumi.distage.testkit.services.dstest

import distage.*
import izumi.distage.config.model.AppConfig
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.framework.services.PlanCircularDependencyCheck
import izumi.distage.model.definition.Binding.SetElementBinding
import izumi.distage.model.definition.ImplDef
import izumi.distage.model.definition.errors.DIError
import izumi.distage.model.effect.QuasiIO.syntax.*
import izumi.distage.model.effect.{QuasiAsync, QuasiIO, QuasiIORunner}
import izumi.distage.model.exceptions.runtime.{IntegrationCheckException, ProvisioningException}
import izumi.distage.model.plan.repr.{DIRendering, KeyMinimizer}
import izumi.distage.model.plan.{ExecutableOp, Plan}
import izumi.distage.modules.DefaultModule
import izumi.distage.modules.support.IdentitySupportModule
import izumi.distage.roles.launcher.{ActivationParser, CLILoggerOptions, LateLoggerFactoryCachingImpl, RoleAppActivationParser}
import izumi.distage.testkit.TestConfig.ParallelLevel
import izumi.distage.testkit.services.dstest.DistageTestRunner.*
import izumi.distage.testkit.services.dstest.DistageTestRunner.MemoizationTree.MemoizationLevelGroup
import izumi.distage.testkit.services.dstest.TestEnvironment.{EnvExecutionParams, MemoizationEnv, PreparedTest}
import izumi.distage.testkit.{DebugProperties, TestActivationStrategy}
import izumi.functional.IzEither.*
import izumi.fundamentals.collections.nonempty.NonEmptyList
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.language.SourceFilePosition
import izumi.fundamentals.platform.time.IzTime
import izumi.logstage.api.logger.LogRouter
import izumi.logstage.api.{IzLogger, Log}

import java.time.temporal.ChronoUnit
import java.util.concurrent.{ConcurrentHashMap, TimeUnit}
import scala.annotation.tailrec
import scala.collection.concurrent.TrieMap
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.{Duration, FiniteDuration}

class DistageTestRunner[F[_]: TagK: DefaultModule](
  reporter: TestReporter,
  isTestSkipException: Throwable => Boolean,
  loggerCache: LateLoggerFactoryCachingImpl.Cache,
) {
  def run(tests: Seq[DistageTest[F]]): Unit = {
    try {
      val start = IzTime.utcNow
      val envs = groupTests(tests, loggerCache).getOrThrow()
      val end = IzTime.utcNow
      logEnvironmentsInfo(envs, ChronoUnit.MILLIS.between(start, end))
      configuredParTraverse[Identity, (MemoizationEnv, MemoizationTree[F])](envs)(_._1.envExec.parallelEnvs) {
        case (e, t) => proceedEnv(e, t)
      }
    } catch {
      case t: Throwable =>
        reporter.onFailure(t)
    } finally {
      reporter.endAll()
    }
  }

  // first we need to plan runtime for our monad. Identity is also supported
  private[this] val runtimeGcRoots: Set[DIKey] = Set(
    DIKey.get[QuasiIORunner[F]],
    DIKey.get[QuasiIO[F]],
    DIKey.get[QuasiAsync[F]],
  )

  /**
    *  Performs tests grouping by it's memoization environment.
    *  [[TestEnvironment.EnvExecutionParams]] - contains only parts of environment that will not affect plan.
    *  Grouping by such structure will allow us to create memoization groups with shared logger and parallel execution policy.
    *  By result you'll got [[TestEnvironment.MemoizationEnv]] mapped to [[MemoizationTree]] - tree-represented memoization plan with tests.
    *  [[TestEnvironment.MemoizationEnv]] represents memoization environment, with shared [[Injector]], and runtime plan.
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
                withRecoverFromFailedExecution(tests) {
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
    import scala.jdk.CollectionConverters._

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
            integrationLogger.log(testkitDebugMessagesLogLevel(env.debugOutput))(
              s"Strengthened weak components: $strengthenedKeys"
            )
          }

          PackedEnv(envMergeCriteria, testPlans, orderedPlans, injector, integrationLogger, highestDebugOutputInTests, strengthenedKeys.toSet)
        }

    }
  }

  def proceedEnv(env: MemoizationEnv, testsTree: MemoizationTree[F]): Unit = {
    val MemoizationEnv(envExec, integrationLogger, runtimePlan, memoizationInjector, _) = env
    val allEnvTests = testsTree.getAllTests.map(_.test)
    integrationLogger.info(s"Processing ${allEnvTests.size -> "tests"} using ${TagK[F].tag -> "monad"}")
    withRecoverFromFailedExecution(allEnvTests) {
      val planChecker = new PlanCircularDependencyCheck(envExec.planningOptions, integrationLogger)

      // producing and verifying runtime plan
      assert(runtimeGcRoots.diff(runtimePlan.keys).isEmpty)
      planChecker.showProxyWarnings(runtimePlan)
      memoizationInjector.produceCustomF[Identity](runtimePlan).use {
        runtimeLocator =>
          val runner = runtimeLocator.get[QuasiIORunner[F]]
          implicit val F: QuasiIO[F] = runtimeLocator.get[QuasiIO[F]]
          implicit val P: QuasiAsync[F] = runtimeLocator.get[QuasiAsync[F]]

          runner.run {
            testsTree.stateTraverseLifecycle(runtimeLocator) {
              case (locator, tree) =>
                planChecker.showProxyWarnings(tree.plan)
                Injector.inherit(locator).produceCustomF[F](tree.plan).evalTap {
                  mainSharedLocator =>
                    proceedMemoizationLevel(planChecker, mainSharedLocator, integrationLogger)(tree.getGroups)
                }
            }(recover = tree => withTestsRecoverCause(None, tree.getAllTests.map(_.test))(_))
          }
      }
    }(onError = ())
  }

  protected def withRecoverFromFailedExecution[A](allTests: Seq[DistageTest[F]])(f: => A)(onError: => A): A = {
    try {
      f
    } catch {
      case t: Throwable =>
        // fail all tests (if an exception reaches here, it must have happened before the runtime was successfully produced)
        allTests.foreach {
          test => reporter.testStatus(test.meta, TestStatus.Failed(t, Duration.Zero))
        }
        reporter.onFailure(t)
        onError
    }
  }

  protected def proceedMemoizationLevel(
    planChecker: PlanCircularDependencyCheck,
    deepestSharedLocator: Locator,
    testRunnerLogger: IzLogger,
  )(levelGroups: Iterable[MemoizationLevelGroup[F]]
  )(implicit
    F: QuasiIO[F],
    P: QuasiAsync[F],
  ): F[Unit] = {
    val testsBySuite = levelGroups.flatMap {
      case MemoizationLevelGroup(preparedTests, strengthenedKeys) =>
        preparedTests.groupBy {
          preparedTest =>
            val testId = preparedTest.test.meta.id
            val parallelLevel = preparedTest.test.environment.parallelSuites
            SuiteData(testId.suiteName, testId.suiteId, testId.suiteClassName, parallelLevel) -> strengthenedKeys
        }
    }
    // now we are ready to run each individual test
    // note: scheduling here is custom also and tests may automatically run in parallel for any non-trivial monad
    // we assume that individual tests within a suite can't have different values of `parallelSuites`
    // (because of structure & that difference even if happens wouldn't be actionable at the level of suites anyway)
    configuredParTraverse(testsBySuite)(_._1._1.parallelLevel) {
      case ((suiteData, strengthenedKeys), preparedTests) =>
        F.bracket(
          acquire = F.maybeSuspend(reporter.beginSuite(suiteData))
        )(release = _ => F.maybeSuspend(reporter.endSuite(suiteData))) {
          _ =>
            configuredParTraverse(preparedTests)(_.test.environment.parallelTests) {
              test => proceedTest(planChecker, deepestSharedLocator, testRunnerLogger, strengthenedKeys)(test)
            }
        }
    }
  }

  protected def proceedTest(
    planChecker: PlanCircularDependencyCheck,
    mainSharedLocator: Locator,
    testRunnerLogger: IzLogger,
    groupStrengthenedKeys: Set[DIKey],
  )(preparedTest: PreparedTest[F]
  )(implicit F: QuasiIO[F]
  ): F[Unit] = {
    val PreparedTest(test, appModule, testPlan, activation) = preparedTest

    val testInjector = Injector.inherit(mainSharedLocator)

    val allSharedKeys = mainSharedLocator.allInstances.map(_.key).toSet
    val newAppModule = appModule.drop(allSharedKeys)
    val newRoots = testPlan.keys -- allSharedKeys ++ groupStrengthenedKeys.intersect(newAppModule.keys)
    val maybeNewTestPlan = if (newRoots.nonEmpty) {
      testInjector.plan(PlannerInput(newAppModule, activation, newRoots)).aggregateErrors
    } else {
      Right(Plan.empty)
    }

    maybeNewTestPlan match {
      case Left(value) =>
        F.maybeSuspend(reporter.testStatus(test.meta, TestStatus.Failed(value, Duration.Zero)))

      case Right(newTestPlan) =>
        val testLogger = testRunnerLogger("testId" -> test.meta.id)
        testLogger.log(testkitDebugMessagesLogLevel(test.environment.debugOutput))(
          s"""Running test...
             |
             |Test plan: $newTestPlan""".stripMargin
        )

        planChecker.showProxyWarnings(newTestPlan)

        proceedIndividual(test, newTestPlan, testInjector)
    }
  }

  protected def proceedIndividual(test: DistageTest[F], testPlan: Plan, testInjector: Injector[F])(implicit F: QuasiIO[F]): F[Unit] = {
    withTestsRecoverCause(None, Seq(test)) {
      if ((DistageTestRunner.enableDebugOutput || test.environment.debugOutput) && testPlan.keys.nonEmpty) {
        reporter.testInfo(test.meta, s"Test plan info: $testPlan")
      }
      testInjector.produceCustomF[F](testPlan).use {
        testLocator =>
          F.suspendF {
            val before = System.nanoTime()
            reporter.testStatus(test.meta, TestStatus.Running)

            withTestsRecoverCause(Some(before), Seq(test)) {
              testLocator
                .run(test.test)
                .flatMap(_ => F.maybeSuspend(reporter.testStatus(test.meta, TestStatus.Succeed(testDuration(Some(before))))))
            }
          }
      }
    }
  }

  protected def withTestsRecoverCause(before: Option[Long], tests: Seq[DistageTest[F]])(testsAction: => F[Unit])(implicit F: QuasiIO[F]): F[Unit] = {
    F.definitelyRecoverCause(testsAction) {
      case (s, _) if isTestSkipException(s) =>
        F.maybeSuspend {
          tests.foreach {
            test => reporter.testStatus(test.meta, TestStatus.Cancelled(s.getMessage, testDuration(before)))
          }
        }
      case (ProvisioningIntegrationException(failures), _) =>
        F.maybeSuspend {
          tests.foreach {
            test => reporter.testStatus(test.meta, TestStatus.Ignored(failures))
          }
        }
      case (_, getTrace) =>
        F.maybeSuspend {
          tests.foreach {
            test => reporter.testStatus(test.meta, TestStatus.Failed(getTrace(), testDuration(before)))
          }
        }
    }
  }

  private[this] def testDuration(before: Option[Long]): FiniteDuration = {
    before.fold(Duration.Zero) {
      before =>
        val after = System.nanoTime()
        FiniteDuration(after - before, TimeUnit.NANOSECONDS)
    }
  }

  protected[this] def loadConfig(env: TestEnvironment, envLogger: IzLogger): AppConfig = {
    DistageTestRunner.memoizedConfig
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

  protected def configuredParTraverse[F1[_], A](
    l: Iterable[A]
  )(getParallelismGroup: A => ParallelLevel
  )(f: A => F1[Unit]
  )(implicit
    F: QuasiIO[F1],
    P: QuasiAsync[F1],
  ): F1[Unit] = {
    val sorted = l.groupBy(getParallelismGroup).toList.sortBy {
      case (ParallelLevel.Unlimited, _) => 1
      case (ParallelLevel.Fixed(_), _) => 2
      case (ParallelLevel.Sequential, _) => 3
    }
    F.traverse_(sorted) {
      case (ParallelLevel.Fixed(n), l) if l.size > 1 => P.parTraverseN_(n)(l)(f)
      case (ParallelLevel.Unlimited, l) if l.size > 1 => P.parTraverse_(l)(f)
      case (_, l) => F.traverse_(l)(f)
    }
  }

  private[this] def logEnvironmentsInfo(envs: Map[MemoizationEnv, MemoizationTree[F]], millis: Long): Unit = {
    val testRunnerLogger = {
      val minimumLogLevel = envs.map(_._1.envExec.logLevel).toSeq.sorted.headOption.getOrElse(Log.Level.Info)
      IzLogger(minimumLogLevel)("phase" -> "testRunner")
    }
    testRunnerLogger.info(s"Creation of memoization trees takes $millis ...")
    val originalEnvSize = envs.iterator.flatMap(_._2.getAllTests.map(_.test.environment)).toSet.size
    val memoizationTreesNum = envs.size

    testRunnerLogger.info(
      s"Created ${memoizationTreesNum -> "memoization trees"} with ${envs.iterator.flatMap(_._2.getAllTests).size -> "tests"} using ${TagK[F].tag -> "monad"}"
    )
    if (originalEnvSize != memoizationTreesNum) {
      testRunnerLogger.info(s"Merged together ${(originalEnvSize - memoizationTreesNum) -> "raw environments"}")
    }

    envs.foreach {
      case (MemoizationEnv(_, _, runtimePlan, _, debugOutput), testTree) =>
        val suites = testTree.getAllTests.map(_.test.meta.id.suiteClassName).toList.distinct
        testRunnerLogger.info(
          s"Memoization environment with ${suites.size -> "suites"} ${testTree.getAllTests.size -> "tests"} ${testTree -> "suitesMemoizationTree"}"
        )
        testRunnerLogger.log(testkitDebugMessagesLogLevel(debugOutput))(
          s"""Effect runtime plan: $runtimePlan"""
        )
    }
  }

  private[this] def testkitDebugMessagesLogLevel(forceDebugOutput: Boolean): Log.Level = {
    if (DistageTestRunner.enableDebugOutput || forceDebugOutput) {
      Log.Level.Info
    } else {
      Log.Level.Debug
    }
  }

}

object DistageTestRunner {
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

  final case class TestId(name: String, suiteName: String, suiteId: String, suiteClassName: String) {
    override def toString: String = s"$suiteName: $name"
  }
  final case class DistageTest[F[_]](test: Functoid[F[Any]], environment: TestEnvironment, meta: TestMeta)
  final case class TestMeta(id: TestId, pos: SourceFilePosition, uid: Long)
  final case class SuiteData(suiteName: String, suiteId: String, suiteClassName: String, parallelLevel: ParallelLevel)

  sealed trait TestStatus
  object TestStatus {
    case object Running extends TestStatus

    sealed trait Done extends TestStatus
    final case class Ignored(checks: NonEmptyList[ResourceCheck.Failure]) extends Done

    sealed trait Finished extends Done
    final case class Cancelled(clue: String, duration: FiniteDuration) extends Finished
    final case class Succeed(duration: FiniteDuration) extends Finished
    final case class Failed(t: Throwable, duration: FiniteDuration) extends Finished
  }

  trait TestReporter {
    def onFailure(f: Throwable): Unit
    def endAll(): Unit
    def beginSuite(id: SuiteData): Unit
    def endSuite(id: SuiteData): Unit
    def testStatus(test: TestMeta, testStatus: TestStatus): Unit
    def testInfo(test: TestMeta, message: String): Unit
  }

  object ProvisioningIntegrationException {
    def unapply(arg: ProvisioningException): Option[NonEmptyList[ResourceCheck.Failure]] = {
      NonEmptyList.from(arg.getSuppressed.iterator.collect { case i: IntegrationCheckException => i.failures.toList }.flatten.toList)
    }
  }

  /**
    * Structure for creation, storage and traversing over memoization levels.
    * To support the memoization level we should create a memoization tree first, where every node will contain a unique part of the memoization plan.
    * For better performance we are going to use mutable structures. Mutate this tree only in case when you KNOW what you doing.
    * Every change in tree structure may lead to test failed across all childs of the corrupted node.
    */
  final class MemoizationTree[F[_]](val plan: Plan) {
    private[this] val children = TrieMap.empty[Plan, MemoizationTree[F]]
    private[this] val groups = ArrayBuffer.empty[MemoizationLevelGroup[F]]

    def getGroups: List[MemoizationLevelGroup[F]] = groups.toList

    def getAllTests: Seq[PreparedTest[F]] = {
      (groups.iterator.flatMap(_.preparedTests) ++ children.iterator.flatMap(_._2.getAllTests)).toSeq
    }

    def addGroup(group: MemoizationLevelGroup[F]): Unit = {
      groups.synchronized(groups.append(group))
      ()
    }

    def add(memoizationTree: MemoizationTree[F]): Unit = {
      children.synchronized(children.put(memoizationTree.plan, memoizationTree))
      ()
    }

    /** Remap tree with function (mapedParentNode, thisNode) => thisNodeMaped */
    @inline def stateMap[A, E](initialState: A)(m: (A, MemoizationTree[F]) => Either[List[E], (A, MemoizationTree[F])]): Either[List[E], MemoizationTree[F]] = {
      m(initialState, this).flatMap {
        case (state, tree) =>
          children.toList
            .biMapAggregate {
              case (_, children) =>
                children.stateMap(state)(m).map(tree.add)
            }.map(_ => tree)
      }
    }

    @inline override def toString: String = render()

    def stateTraverseLifecycle[State](
      initialState: State
    )(func: (State, MemoizationTree[F]) => Lifecycle[F, State]
    )(recover: MemoizationTree[F] => F[Unit] => F[Unit]
    )(implicit F: QuasiIO[F]
    ): F[Unit] = {
      recover(this) {
        func(initialState, this).use {
          nextState =>
            F.traverse_(children) {
              case (_, tree) =>
                tree.stateTraverseLifecycle(nextState)(func)(recover)
            }
        }
      }
    }

    @tailrec private def addGroupByPath(path: List[Plan], levelTests: MemoizationLevelGroup[F]): Unit = {
      path match {
        case Nil =>
          addGroup(levelTests)
        case node :: tail =>
          val childTree = children.synchronized(children.getOrElseUpdate(node, new MemoizationTree[F](node)))
          childTree.addGroupByPath(tail, levelTests)
      }
    }

    private def render(level: Int = 0, suitePad: String = "", levelPad: String = ""): String = {
      val memoizationRoots = plan.keys
      val levelInfo = if (plan.keys.nonEmpty) {
        val minimizer = KeyMinimizer(memoizationRoots, DIRendering.colorsEnabled)
        memoizationRoots.iterator.map(minimizer.renderKey).mkString("[ ", ", ", " ]")
      } else {
        "ø"
      }
      val currentLevelPad = {
        val emptyStep = if (suitePad.isEmpty) "" else s"\n${suitePad.dropRight(5)}║"
        s"$emptyStep\n$levelPad╗ LEVEL = $level;\n$suitePad║ MEMOIZATION ROOTS: $levelInfo"
      }

      val str = {
        val testIds = groups.toList.flatMap(_.preparedTests.map(_.test.meta.id.suiteName)).distinct.sorted.map(t => s"$suitePad╠══* $t")

        if (testIds.nonEmpty) s"$currentLevelPad\n${testIds.mkString("\n")}" else currentLevelPad
      }

      val updatedLevelPad: String = levelPad.replaceAll("╠════$", "║    ").replaceAll("╚════$", "     ")

      children.toList.zipWithIndex.foldLeft(str) {
        case (acc, ((_, nextTree), i)) =>
          val isLastChild = children.size == i + 1
          val nextSuitePad = suitePad + (if (isLastChild) "     " else "║    ")
          val nextLevelPad = level match {
            case 0 if isLastChild => "╚════"
            case _ if isLastChild => s"$updatedLevelPad╚════"
            case _ => s"$updatedLevelPad╠════"
          }
          val nextChildStr = nextTree.render(level + 1, nextSuitePad, nextLevelPad)
          s"$acc$nextChildStr"
      }
    }
  }
  object MemoizationTree {
    final case class MemoizationLevelGroup[F[_]](preparedTests: Iterable[PreparedTest[F]], strengthenedKeys: Set[DIKey])
    def apply[F[_]](iterator: Iterable[PackedEnv[F]]): MemoizationTree[F] = {
      val tree = new MemoizationTree[F](Plan.empty)
      // usually, we have a small amount of levels, so parallel executions make only worse here
      iterator.foreach {
        env =>
          val plans = env.memoizationPlanTree.filter(_.plan.meta.nodes.nonEmpty)
          tree.addGroupByPath(plans, MemoizationLevelGroup(env.preparedTests, env.strengthenedKeys))
      }
      tree
    }
  }

  private final val enableDebugOutput: Boolean = DebugProperties.`izumi.distage.testkit.debug`.boolValue(false)

  private final val memoizedConfig = new ConcurrentHashMap[(String, BootstrapFactory, Option[AppConfig]), AppConfig]

}
