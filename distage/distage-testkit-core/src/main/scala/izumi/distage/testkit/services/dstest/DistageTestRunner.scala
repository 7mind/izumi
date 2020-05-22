package izumi.distage.testkit.services.dstest

import java.util.concurrent.{Executors, TimeUnit}

import distage.{Activation, DIKey, Injector, PlannerInput}
import izumi.distage.config.model.AppConfig
import izumi.distage.framework.model.IntegrationCheck
import izumi.distage.framework.model.exceptions.IntegrationCheckException
import izumi.distage.framework.services.{IntegrationChecker, PlanCircularDependencyCheck}
import izumi.distage.model.Locator
import izumi.distage.model.definition.Binding.SetElementBinding
import izumi.distage.model.definition.{ImplDef, ModuleBase}
import izumi.distage.model.effect.DIEffect.syntax._
import izumi.distage.model.effect.DIEffectAsync.NamedThreadFactory
import izumi.distage.model.effect.{DIEffect, DIEffectAsync, DIEffectRunner}
import izumi.distage.model.exceptions.ProvisioningException
import izumi.distage.model.plan.{OrderedPlan, TriSplittedPlan}
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.roles.services.EarlyLoggers
import izumi.distage.testkit.DebugProperties
import izumi.distage.testkit.services.dstest.DistageTestRunner._
import izumi.distage.testkit.services.dstest.TestEnvironment.{AppModuleTestGroup, MemoizationEnvWithPlan, MemoizationEnvironment}
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.language.CodePosition
import izumi.fundamentals.platform.strings.IzString._
import izumi.logstage.api.IzLogger
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
    debugLogEnvs(envs)
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
    *  [[TestEnvironment.MemoizationEnvironment]] - is the same as [[TestEnvironment]] except of `appModule`.
    *  Grouping by such structure will allow us to create specific memoization environment with shared instances that being memoized.
    *  By result you'll got [[MemoizationEnvWithPlan]] mapped to it's [[AppModuleTestGroup]]s.
    *  [[MemoizationEnvWithPlan]] represents memoization environment, with planning instances and shared [[TriSplittedPlan]].
    *  Each [[AppModuleTestGroup]] represents a bunch of tests with similar `appModule`.
    * */
  def groupTests(distageTests: Seq[DistageTest[F]]): Iterable[(MemoizationEnvWithPlan, Iterable[AppModuleTestGroup[F]])] = {
    // here we are grouping our test by memoization env
    distageTests.groupBy(_.environment.toMemoizationEnv).flatMap {
      case (env, tests) =>
        // creating logger and config loader defined by [[MemoizationEnvironment]]
        val earlyPhaseLogger = IzLogger(env.testRunnerLogLevel)("phase" -> "env")
        val configLoader = env.bootstrapFactory.makeConfigLoader(env.configBaseName, earlyPhaseLogger).map {
          c =>
            env.configOverrides match {
              case None => c
              case Some(overrides) =>
                AppConfig(overrides.config.withFallback(c.config).resolve())
            }
        }
        val config = configLoader.loadConfig()
        val testRunnerLogger = EarlyLoggers.makeLateLogger(RawAppArgs.empty, earlyPhaseLogger, config, env.testRunnerLogLevel, defaultLogFormatJson = false)
        // here we scan our classpath to enumerate of our components (we have "bootstrap" components - injector plugins, and app components)
        val options = env.planningOptions
        val provider = env.bootstrapFactory.makeModuleProvider[F](options, config, testRunnerLogger.router, env.roles, env.activationInfo, env.activation)
        val bsModule = provider.bootstrapModules().merge overridenBy env.bsModule
        val injector = Injector(env.activation, bsModule)
        // grouping our tests by it's shared memoized plan and runtime plan
        tests
          .map {
            distageTest =>
              val appModule = provider.appModules().merge overridenBy distageTest.environment.appModule
              val shared = injector.trisectByKeys(appModule, (env.memoizationRoots ++ env.forcedRoots) -- runtimeGcRoots) {
                _.collectChildren[IntegrationCheck].map(_.target).toSet
              }
              val runtimePlan = injector.plan(PlannerInput(appModule, runtimeGcRoots))
              ((shared, runtimePlan), appModule -> distageTest)
          }.groupBy(_._1).map {
            case ((shared, runtimePlan), tests) =>
              MemoizationEnvWithPlan(env, testRunnerLogger, injector, shared, runtimePlan) -> tests.groupBy(_._2._1).map {
                case (m, t) => AppModuleTestGroup(m, t.map(_._2._2))
              }
          }
    }
  }

  def runEnvs(parallel: Boolean)(envs: Iterable[(MemoizationEnvWithPlan, Iterable[AppModuleTestGroup[F]])]): Unit = {
    configuredForeach(parallel)(envs) {
      case (MemoizationEnvWithPlan(env, testRunnerLogger, injector, memoizationPlan, runtimePlan), moduleGroups) =>
        val allGroupsTest = moduleGroups.flatMap(_.distageTests)
        val memoizationEnvLogger = testRunnerLogger.apply("memoEnv" -> env.hashCode)
        memoizationEnvLogger.info(
          s"Processing ${moduleGroups.size -> "moduleEnvs"} with ${allGroupsTest.size -> "tests"} using ${TagK[F].tag -> "monad"}, ${env.configBaseName}, ${env.activation}"
        )
        memoizationEnvLogger.debug(s"Processing test suites: ${allGroupsTest.map(_.meta.id.suiteClassName).toSet.niceList() -> "testNames"}")
        withReportOnFailedExecution(allGroupsTest) {
          val checker = new IntegrationChecker.Impl[F](memoizationEnvLogger)
          val options = env.planningOptions
          val planCheck = new PlanCircularDependencyCheck(options, memoizationEnvLogger)
          assert(runtimeGcRoots.diff(runtimePlan.keys).isEmpty)

          planCheck.verify(runtimePlan)
          injector.produceF[Identity](runtimePlan).use {
            runtimeLocator =>
              val runner = runtimeLocator.get[DIEffectRunner[F]]
              implicit val F: DIEffect[F] = runtimeLocator.get[DIEffect[F]]
              implicit val P: DIEffectAsync[F] = runtimeLocator.get[DIEffectAsync[F]]

              runner.run {
                withTestsRecoverCase(allGroupsTest) {
                  // during memoized plan producing we are going to skip primary plan, because it will not contain instances for our tests
                  withIntegrationSharedPlan(runtimeLocator, planCheck, checker, memoizationPlan, allGroupsTest) {
                    memoizedIntegrationLocator =>
                      // all keys that already contains in memoized plan
                      val memoizedSharedKeys = memoizedIntegrationLocator.allInstances.map(_.key).toSet
                      val previouslyCreatedKeys = runtimeGcRoots ++ memoizedSharedKeys
                      configuredTraverse_(env.parallelSuites)(moduleGroups) {
                        case AppModuleTestGroup(appModule, tests) =>
                          val moduleGroupLogger = memoizationEnvLogger.apply("moduleGroup" -> appModule.hashCode())
                          moduleGroupLogger.info(s"Processing ${tests.size -> "tests"}")
                          moduleGroupLogger.debug(s"Processing ${tests.map(_.meta.id.suiteClassName).niceList() -> "tests"}")
                          // complete plan for each test
                          val testPlans = tests.map {
                            distageTest =>
                              val keys = distageTest.test.get.diKeys.toSet
                              val testRoots = keys ++ env.forcedRoots
                              val plan = if (testRoots.nonEmpty) injector.plan(PlannerInput(appModule, testRoots)) else OrderedPlan.empty
                              distageTest -> plan
                          }
                          // here we find all the shared components in each of our individual tests
                          val moduleGroupKeys = testPlans.flatMap(_._2.steps).map(_.target).toSet
                          val sharedKeys = moduleGroupKeys.intersect(env.memoizationRoots) -- previouslyCreatedKeys
                          moduleGroupLogger.info(s"Memoized components: $sharedKeys")

                          // we need to "strengthen" all weak set instances that occur in our tests to ensure that they will persist in each of test.
                          val (strengthenedKeys, strengthenedAppModule) = appModule.drop(previouslyCreatedKeys).foldLeftWith(List.empty[DIKey]) {
                            case (acc, b @ SetElementBinding(key, r: ImplDef.ReferenceImpl, _, _)) if r.weak && moduleGroupKeys(key) =>
                              (key :: acc) -> b.copy(implementation = r.copy(weak = false))
                            case (acc, b) =>
                              acc -> b
                          }
                          if (strengthenedKeys.nonEmpty) {
                            moduleGroupLogger.info(s"Strengthened weak components: $strengthenedKeys")
                          }

                          // compute [[TriSplittedPlan]] of our test, to extract shared plan, and perform it only once
                          val shared = injector.trisectByKeys(strengthenedAppModule, sharedKeys) {
                            _.collectChildren[IntegrationCheck].map(_.target).toSet
                          }

                          debugLogger.log(s"""Tests in module group ${appModule.hashCode()}: ${tests.size}
                            |Integration plan: ${shared.side}
                            |Memoized plan: ${shared.shared}
                            |App plan: ${shared.primary}
                            |""".stripMargin)

                          // now we can produce our shared plan, check it's integration, and perform tests execution
                          planCheck.verify(shared.shared)
                          withTestsRecoverCase(tests) {
                            withIntegrationSharedPlan(memoizedIntegrationLocator, planCheck, checker, shared, tests) {
                              proceed(env, checker, appModule, planCheck, testPlans, shared, _)
                            }
                          }
                      }
                  }
                }
              }
          }
        }
    }
  }

  private[this] def withIntegrationSharedPlan(
    locator: Locator,
    planCheck: PlanCircularDependencyCheck,
    checker: IntegrationChecker[F],
    plan: TriSplittedPlan,
    tests: Iterable[DistageTest[F]],
  )(use: Locator => F[Unit]
  )(implicit DIEffect: DIEffect[F]
  ): F[Unit] = {
    // shared plan
    planCheck.verify(plan.shared)
    Injector.inherit(locator).produceF[F](plan.shared).use {
      sharedLocator =>
        // integration plan
        planCheck.verify(plan.side)
        Injector.inherit(sharedLocator).produceF[F](plan.side).use {
          integrationSharedLocator =>
            withIntegrationCheck(checker, integrationSharedLocator)(tests, plan) {
              use(integrationSharedLocator)
            }
        }
    }
  }

  def withTestsRecoverCase(tests: => Iterable[DistageTest[F]])(testsAction: F[Unit])(implicit F: DIEffect[F]): F[Unit] = {
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
    integLocator: Locator,
  )(tests: Iterable[DistageTest[F]],
    plans: TriSplittedPlan,
  )(onSuccess: => F[Unit]
  )(implicit F: DIEffect[F]
  ): F[Unit] = {
    checker.collectFailures(plans.side.declaredRoots, integLocator).flatMap {
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
    ichecker: IntegrationChecker[F],
    appmodule: ModuleBase,
    checker: PlanCircularDependencyCheck,
    testplans: Seq[(DistageTest[F], OrderedPlan)],
    shared: TriSplittedPlan,
    parent: Locator,
  )(implicit F: DIEffect[F],
    P: DIEffectAsync[F],
  ): F[Unit] = {
    // here we produce our shared plan
    checker.verify(shared.primary)

    Injector.inherit(parent).produceF[F](shared.primary).use {
      mainSharedLocator =>
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
                    val newtestplan = testInjector.trisectByRoots(appmodule.drop(allSharedKeys), testplan.keys -- allSharedKeys, integrations)

                    debugLogger.log(s"Running `test Id`=${test.meta.id}")

                    checker.verify(newtestplan.primary)
                    checker.verify(newtestplan.side)
                    checker.verify(newtestplan.shared)

                    // we are ready to run the test, finally
                    testInjector.produceF[F](newtestplan.shared).use {
                      sharedLocator =>
                        Injector.inherit(sharedLocator).produceF[F](newtestplan.side).use {
                          integLocator =>
                            withIntegrationCheck(ichecker, integLocator)(Seq(test), newtestplan) {
                              proceedIndividual(test, newtestplan, sharedLocator)
                            }
                        }
                    }
                }
            }
        }
    }
  }

  protected def proceedIndividual(test: DistageTest[F], newtestplan: TriSplittedPlan, parent: Locator)(implicit F: DIEffect[F]): F[Unit] = {
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
      Injector.inherit(parent).produceF[F](newtestplan.primary).use {
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

  protected def debugLogEnvs(memoizedEnvs: Iterable[(MemoizationEnvWithPlan, Iterable[AppModuleTestGroup[F]])]): Unit = {
    debugLogger.log {
      val printedEnvs = memoizedEnvs
        .toList.map {
          case (MemoizationEnvWithPlan(env, _, _, _, _), moduleGroups) =>
            val tests = moduleGroups.flatMap(_.distageTests)
            final case class DebugTestEnvironment(Activation: Activation, memoizationRoots: DIKey => Boolean, tests: String)
            DebugTestEnvironment(env.activation, env.memoizationRoots, tests.map(_.meta.id).niceList().shift(2))
        }.niceList()
      s"Env contents: $printedEnvs"
    }
  }

  protected val debugLogger: TrivialLogger = TrivialLogger.make[DistageTestRunner[F]](DebugProperties.`izumi.distage.testkit.debug`)
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

}
