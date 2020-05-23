package izumi.distage.testkit.services.dstest

import java.util.concurrent.{ConcurrentHashMap, Executors, TimeUnit}

import distage.{DIKey, Injector, PlannerInput}
import izumi.distage.config.model.AppConfig
import izumi.distage.framework.model.IntegrationCheck
import izumi.distage.framework.model.exceptions.IntegrationCheckException
import izumi.distage.framework.services.{IntegrationChecker, PlanCircularDependencyCheck}
import izumi.distage.model.Locator
import izumi.distage.model.definition.Binding.SetElementBinding
import izumi.distage.model.definition.ImplDef
import izumi.distage.model.effect.DIEffect.syntax._
import izumi.distage.model.effect.DIEffectAsync.NamedThreadFactory
import izumi.distage.model.effect.{DIEffect, DIEffectAsync, DIEffectRunner}
import izumi.distage.model.exceptions.ProvisioningException
import izumi.distage.model.plan.{OrderedPlan, TriSplittedPlan}
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.roles.services.EarlyLoggers
import izumi.distage.testkit.DebugProperties
import izumi.distage.testkit.services.dstest.DistageTestRunner._
import izumi.distage.testkit.services.dstest.TestEnvironment.{MemoizationEnvWithPlan, MemoizationEnvironment}
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.language.CodePosition
import izumi.fundamentals.platform.strings.IzString._
import izumi.logstage.api.{IzLogger, Log}
import izumi.reflect.TagK

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{Duration, FiniteDuration}

class DistageTestRunner[F[_]: TagK](
  reporter: TestReporter,
  tests: Seq[DistageTest[F]],
  isTestSkipException: Throwable => Boolean,
) {
  def run(): Unit = {
    val envs = groupTests(tests)
    logEnvironmentsInfo(envs)
    try {
      val (parallelEnvs, sequentialEnvs) = envs.partition(_._1.env.parallelEnvs)
      runEnvs(parallel = true)(parallelEnvs)
      runEnvs(parallel = false)(sequentialEnvs)
    } finally reporter.endAll()
  }

  // first we need to plan runtime for our monad. Identity is also supported
  private[this] val runtimeGcRoots: Set[DIKey] = Set(
    DIKey.get[DIEffectRunner[F]],
    DIKey.get[DIEffect[F]],
    DIKey.get[DIEffectAsync[F]],
  )

  /**
    *  Performs tests grouping by it's memoization environment.
    *  [[TestEnvironment.MemoizationEnvironment]] - contains only parts of environment that will not affect plan.
    *  Grouping by such structure will allow us to create memoization groups with shared logger and parallel execution policy.
    *  By result you'll got [[MemoizationEnvWithPlan]] mapped to tests wit it's plans.
    *  [[MemoizationEnvWithPlan]] represents memoization environment, with shared [[TriSplittedPlan]], [[Injector]], and runtime plan.
    * */
  def groupTests(distageTests: Seq[DistageTest[F]]): Map[MemoizationEnvWithPlan, Seq[(DistageTest[F], OrderedPlan)]] = {

    final case class EnvByproducts(injector: Injector, envLogger: IzLogger)

    // here we are grouping our tests by memoization env
    distageTests.groupBy(_.environment.toMemoizationEnv).flatMap {
      case (memoEnv, grouped) =>
        val configLoadLogger = IzLogger(memoEnv.logLevel).withCustomContext("phase" -> "testRunner")
        val memoizationEnvs = grouped.groupBy(_.environment).map {
          case (env, tests) =>
            // make a config loader for current env with logger
            val config = loadConfig(env, configLoadLogger)
            val lateLogger = EarlyLoggers.makeLateLogger(RawAppArgs.empty, configLoadLogger, config, memoEnv.logLevel, defaultLogFormatJson = false)

            // here we scan our classpath to enumerate of our components (we have "bootstrap" components - injector plugins, and app components)
            val options = env.planningOptions
            val provider = env.bootstrapFactory.makeModuleProvider[F](options, config, lateLogger.router, env.roles, env.activationInfo, env.activation)
            val bsModule = provider.bootstrapModules().merge overridenBy env.bsModule
            val appModule = provider.appModules().merge overridenBy env.appModule
            val injector = Injector(env.activation, bsModule)

            // runtime plan with `runtimeGcRoots`
            val runtimePlan = injector.plan(PlannerInput(appModule, runtimeGcRoots))
            // all keys created in runtimePlan, we filter them out later to not recreate any components already in runtimeLocator
            val runtimeKeys = runtimePlan.keys

            // produce plan for each test
            val testPlans = tests.map {
              distageTest =>
                val keys = distageTest.test.get.diKeys.toSet
                val testRoots = keys ++ env.forcedRoots
                val plan = if (testRoots.nonEmpty) injector.plan(PlannerInput(appModule, testRoots)) else OrderedPlan.empty
                distageTest -> plan
            }
            val envKeys = testPlans.flatMap(_._2.keys).toSet
            val sharedKeys = envKeys.intersect(env.memoizationRoots) -- runtimeKeys

            // we need to "strengthen" all _memoized_ weak set instances that occur in our tests to ensure that they
            // be created and persist in memoized set. we do not use strengthened bindings afterwards, so non-memoized
            // weak sets behave as usual
            val (strengthenedKeys, strengthenedAppModule) = appModule.drop(runtimeKeys).foldLeftWith(List.empty[DIKey]) {
              case (acc, b @ SetElementBinding(key, r: ImplDef.ReferenceImpl, _, _)) if r.weak && (envKeys(key) || envKeys(r.key)) =>
                (key :: acc) -> b.copy(implementation = r.copy(weak = false))
              case (acc, b) =>
                acc -> b
            }

            // compute [[TriSplittedPlan]] of our test, to extract shared plan, and perform it only once
            val shared = injector.trisectByKeys(strengthenedAppModule, sharedKeys) {
              _.collectChildren[IntegrationCheck].map(_.target).toSet
            }

            val memoEnvHashCode = (shared, runtimePlan, memoEnv).hashCode()
            val integrationLogger = lateLogger("memoEnv" -> memoEnvHashCode)
            if (strengthenedKeys.nonEmpty) {
              integrationLogger.log(testkitDebugMessagesLogLevel(env.debugOutput))(
                s"Strengthened weak components: $strengthenedKeys"
              )
            }

            ((shared, runtimePlan), testPlans, EnvByproducts(injector, integrationLogger))
        }
        // merge environments together by equality of their shared & runtime plans
        // in a lot of cases memoization plan will be the same even with many minor changes to TestConfig,
        // so this saves a lot of realloaction of memoized resoorces
        memoizationEnvs.groupBy(_._1).map {
          case ((shared, runtimePlan), memoEnvContents) =>
            val EnvByproducts(injector, integrationLogger) = memoEnvContents.head._3
            val tests = memoEnvContents.iterator.flatMap(_._2).toSeq

            MemoizationEnvWithPlan(memoEnv, integrationLogger, shared, runtimePlan, injector) -> tests
        }
    }
  }

  def runEnvs(parallel: Boolean)(envs: Map[MemoizationEnvWithPlan, Iterable[(DistageTest[F], OrderedPlan)]]): Unit = {
    configuredForeach(parallel)(envs) {
      case (MemoizationEnvWithPlan(env, integrationLogger, memoizationPlan, runtimePlan, injector), testPlans) =>
        val allEnvTests = testPlans.map(_._1)
        integrationLogger.info(s"Processing ${allEnvTests.size -> "tests"} using ${TagK[F].tag -> "monad"}")
        withReportOnFailedExecution(allEnvTests) {
          val checker = new IntegrationChecker.Impl[F](integrationLogger)
          val planCheck = new PlanCircularDependencyCheck(env.planningOptions, integrationLogger)

          // producing and verifying runtime plan
          assert(runtimeGcRoots.diff(runtimePlan.keys).isEmpty)
          planCheck.verify(runtimePlan)
          injector.produceF[Identity](runtimePlan).use {
            runtimeLocator =>
              val runner = runtimeLocator.get[DIEffectRunner[F]]
              implicit val F: DIEffect[F] = runtimeLocator.get[DIEffect[F]]
              implicit val P: DIEffectAsync[F] = runtimeLocator.get[DIEffectAsync[F]]

              runner.run {
                withTestsRecoverCase(allEnvTests) {
                  // producing full runtime plan with all shared keys
                  withIntegrationSharedPlan(runtimeLocator, planCheck, checker, memoizationPlan, allEnvTests) {
                    memoizedIntegrationLocator =>
                      proceed(env, checker, planCheck, testPlans, memoizedIntegrationLocator, integrationLogger)
                  }
                }
              }
          }
        }
    }
  }

  private[this] def withIntegrationSharedPlan(
    runtimeLocator: Locator,
    planCheck: PlanCircularDependencyCheck,
    checker: IntegrationChecker[F],
    plan: TriSplittedPlan,
    tests: => Iterable[DistageTest[F]],
  )(use: Locator => F[Unit]
  )(implicit
    F: DIEffect[F]
  ): F[Unit] = {
    // shared plan
    planCheck.verify(plan.shared)
    Injector.inherit(runtimeLocator).produceF[F](plan.shared).use {
      sharedLocator =>
        // integration plan
        planCheck.verify(plan.side)
        Injector.inherit(sharedLocator).produceF[F](plan.side).use {
          integrationSharedLocator =>
            withIntegrationCheck(checker, integrationSharedLocator)(tests, plan) {
              // main plan
              planCheck.verify(plan.primary)
              Injector.inherit(integrationSharedLocator).produceF[F](plan.primary).use {
                mainSharedLocator =>
                  use(mainSharedLocator)
              }
            }
        }
    }
  }

  def withTestsRecoverCase(tests: => Iterable[DistageTest[F]])(testsAction: => F[Unit])(implicit F: DIEffect[F]): F[Unit] = {
    F.definitelyRecoverCause {
      testsAction
    } {
      case (ProvisioningIntegrationException(integrations), _) =>
        // FIXME: temporary hack to allow missing containers to skip tests (happens when both DockerWrapper & integration check that depends on Docker.Container are memoized)
        F.maybeSuspend(ignoreIntegrationCheckFailedTests(tests, integrations))
      case (_, getTrace) =>
        // fail all tests (if an exception reached here, it must have happened before the individual test runs)
        F.maybeSuspend(failAllTests(tests, getTrace()))
    }
  }

  private[this] def withReportOnFailedExecution(allTests: => Iterable[DistageTest[F]])(f: => Unit): Unit = {
    try {
      f
    } catch {
      case t: Throwable =>
        // fail all tests (if an exception reaches here, it must have happened before the runtime was successfully produced)
        failAllTests(allTests.toSeq, t)
        reporter.onFailure(t)
    }
  }

  protected def withIntegrationCheck(
    checker: IntegrationChecker[F],
    integrationLocator: Locator,
  )(tests: => Iterable[DistageTest[F]],
    plans: TriSplittedPlan,
  )(onSuccess: => F[Unit]
  )(implicit
    F: DIEffect[F]
  ): F[Unit] = {
    checker.collectFailures(plans.side.declaredRoots, integrationLocator).flatMap {
      case Left(failures) =>
        F.maybeSuspend {
          ignoreIntegrationCheckFailedTests(tests, failures)
        }
      case Right(_) =>
        onSuccess
    }
  }

  protected def ignoreIntegrationCheckFailedTests(tests: Iterable[DistageTest[F]], failures: Seq[ResourceCheck.Failure]): Unit = {
    tests.foreach {
      test =>
        reporter.testStatus(test.meta, TestStatus.Ignored(failures))
    }
  }

  protected def failAllTests(tests: Iterable[DistageTest[F]], t: Throwable): Unit = {
    tests.foreach {
      test =>
        reporter.testStatus(test.meta, TestStatus.Failed(t, Duration.Zero))
    }
  }

  protected def proceed(
    env: MemoizationEnvironment,
    integrationChecker: IntegrationChecker[F],
    checker: PlanCircularDependencyCheck,
    testplans: Iterable[(DistageTest[F], OrderedPlan)],
    mainSharedLocator: Locator,
    testRunnerLogger: IzLogger,
  )(implicit
    F: DIEffect[F],
    P: DIEffectAsync[F],
  ): F[Unit] = {
    val testInjector = Injector.inherit(mainSharedLocator)
    val testsBySuite = testplans.groupBy {
      t =>
        val testId = t._1.meta.id
        SuiteData(testId.suiteName, testId.suiteId, testId.suiteClassName)
    }
    // now we are ready to run each individual test
    // note: scheduling here is custom also and tests may automatically run in parallel for any non-trivial monad
    configuredTraverse_(env.parallelSuites)(testsBySuite) {
      case (suiteData, plans) =>
        F.bracket(
          acquire = F.maybeSuspend(reporter.beginSuite(suiteData))
        )(release = _ => F.maybeSuspend(reporter.endSuite(suiteData))) {
          _ =>
            configuredTraverse_(env.parallelTests)(plans) {
              case (test, testplan) =>
                val allSharedKeys = mainSharedLocator.allInstances.map(_.key).toSet

                val integrations = testplan.collectChildren[IntegrationCheck].map(_.target).toSet -- allSharedKeys
                val newTestPlan = testInjector.trisectByRoots(test.environment.appModule.drop(allSharedKeys), testplan.keys -- allSharedKeys, integrations)

                testRunnerLogger("testId" -> test.meta.id).log(testkitDebugMessagesLogLevel(env.debugOutput))("Running...")

                checker.verify(newTestPlan.primary)
                checker.verify(newTestPlan.side)
                checker.verify(newTestPlan.shared)

                // we are ready to run the test, finally
                testInjector.produceF[F](newTestPlan.shared).use {
                  sharedLocator =>
                    Injector.inherit(sharedLocator).produceF[F](newTestPlan.side).use {
                      integrationLocator =>
                        withIntegrationCheck(integrationChecker, integrationLocator)(Seq(test), newTestPlan) {
                          proceedIndividual(test, newTestPlan.primary, sharedLocator)
                        }
                    }
                }
            }
        }
    }
  }

  protected def proceedIndividual(test: DistageTest[F], testPlan: OrderedPlan, parent: Locator)(implicit F: DIEffect[F]): F[Unit] = {
    def testDuration(before: Option[Long]): FiniteDuration = {
      before.fold(Duration.Zero) {
        before =>
          val after = System.nanoTime()
          FiniteDuration(after - before, TimeUnit.NANOSECONDS)
      }
    }

    def doRecover(before: Option[Long])(action: => F[Unit]): F[Unit] = {
      F.definitelyRecoverCause(action) {
        case (s, _) if isTestSkipException(s) =>
          F.maybeSuspend {
            reporter.testStatus(test.meta, TestStatus.Cancelled(s.getMessage, testDuration(before)))
          }
        case (_, getTrace) =>
          F.maybeSuspend {
            reporter.testStatus(test.meta, TestStatus.Failed(getTrace(), testDuration(before)))
          }
      }
    }

    doRecover(None) {
      Injector.inherit(parent).produceF[F](testPlan).use {
        testLocator =>
          F.suspendF {
            val before = System.nanoTime()
            reporter.testStatus(test.meta, TestStatus.Running)

            doRecover(Some(before)) {
              testLocator
                .run(test.test)
                .flatMap(_ => F.maybeSuspend(reporter.testStatus(test.meta, TestStatus.Succeed(testDuration(Some(before))))))
            }
          }
      }
    }
  }

  protected[this] def loadConfig(env: TestEnvironment, envLogger: IzLogger): AppConfig = {
    DistageTestRunner
      .memoizedConfig
      .computeIfAbsent(
        (env.configBaseName, env.bootstrapFactory, env.configOverrides),
        _ => {
          val configLoader = env
            .bootstrapFactory
            .makeConfigLoader(env.configBaseName, envLogger)
            .map {
              c =>
                env.configOverrides match {
                  case Some(overrides) =>
                    AppConfig(overrides.config.withFallback(c.config).resolve())
                  case None =>
                    c
                }
            }
          configLoader.loadConfig()
        },
      )
  }

  protected def configuredTraverse_[A](parallel: Boolean)(l: Iterable[A])(f: A => F[Unit])(implicit F: DIEffect[F], P: DIEffectAsync[F]): F[Unit] = {
    if (parallel) {
      P.parTraverse_(l)(f)
    } else {
      F.traverse_(l)(f)
    }
  }

  protected def configuredForeach[A](parallelEnvs: Boolean)(environments: Iterable[A])(f: A => Unit): Unit = {
    if (parallelEnvs && environments.size > 1) {
      val ec = ExecutionContext.fromExecutorService {
        Executors.newCachedThreadPool(
          new NamedThreadFactory(
            "distage-test-runner-cached-pool",
            daemon = true,
          )
        )
      }
      try {
        DIEffectAsync.diEffectParIdentity.parTraverse_(environments)(f)
      } finally ec.shutdown()
    } else {
      environments.foreach(f)
    }
  }

  private[this] def logEnvironmentsInfo(envs: Map[MemoizationEnvWithPlan, Iterable[(DistageTest[F], OrderedPlan)]]): Unit = {
    val testRunnerLogger = {
      val minimumLogLevel = envs.map(_._1.env.logLevel).toSeq.sorted.headOption.getOrElse(Log.Level.Info)
      IzLogger(minimumLogLevel)("phase" -> "testRunner")
    }
    val originalEnvSize = envs.iterator.flatMap(_._2.map(_._1.environment)).toSet.size
    val memoEnvsSize = envs.size

    testRunnerLogger.info(s"Created ${memoEnvsSize -> "memoization envs"} with ${envs.iterator.flatMap(_._2).size -> "tests"} using ${TagK[F].tag -> "monad"}")
    if (originalEnvSize != memoEnvsSize) {
      testRunnerLogger.info(s"Merged together ${(originalEnvSize - memoEnvsSize) -> "raw environments"}")
    }

    envs.foreach {
      case (MemoizationEnvWithPlan(env, testEnvLogger, memoizationPlan, _, _), tests) =>
        val suites = tests.map(_._1.meta.id.suiteClassName).toList.distinct
        testEnvLogger.info(s"Memoization environment with ${suites.size -> "suites"} ${tests.size -> "tests"} ${suites.sorted.niceList() -> "testSuites"}")
        testEnvLogger.log(testkitDebugMessagesLogLevel(env.debugOutput))(
          s"""Integration plan: ${memoizationPlan.side}
             |
             |Memoized plan: ${memoizationPlan.shared}
             |
             |App plan: ${memoizationPlan.primary}""".stripMargin
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
  final case class TestId(name: String, suiteName: String, suiteId: String, suiteClassName: String)
  final case class DistageTest[F[_]](test: ProviderMagnet[F[_]], environment: TestEnvironment, meta: TestMeta)
  final case class TestMeta(id: TestId, pos: CodePosition, uid: Long)
  final case class SuiteData(suiteName: String, suiteId: String, suiteClassName: String)

  sealed trait TestStatus
  object TestStatus {
    case object Running extends TestStatus

    sealed trait Done extends TestStatus
    final case class Ignored(checks: Seq[ResourceCheck.Failure]) extends Done

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
  }

  object ProvisioningIntegrationException {
    def unapply(arg: ProvisioningException): Option[Seq[ResourceCheck.Failure]] = {
      Some(arg.getSuppressed.collect { case i: IntegrationCheckException => i.failures }.toSeq)
        .filter(_.nonEmpty)
        .map(_.flatten)
    }
  }

  private val enableDebugOutput = DebugProperties.`izumi.distage.testkit.debug`.asBoolean(false)

  private val memoizedConfig = new ConcurrentHashMap[(String, BootstrapFactory, Option[AppConfig]), AppConfig]()

}
