package izumi.distage.testkit.services.dstest

import java.util.concurrent.TimeUnit

import distage.{Activation, DIKey, Injector, PlannerInput}
import izumi.distage.framework.model.IntegrationCheck
import izumi.distage.framework.model.exceptions.IntegrationCheckException
import izumi.distage.framework.services.{IntegrationChecker, PlanCircularDependencyCheck}
import izumi.distage.model.Locator
import izumi.distage.model.definition.Binding.SetElementBinding
import izumi.distage.model.definition.{ImplDef, ModuleBase}
import izumi.distage.model.effect.DIEffect.syntax._
import izumi.distage.model.effect.{DIEffect, DIEffectAsync, DIEffectRunner}
import izumi.distage.model.exceptions.ProvisioningException
import izumi.distage.model.plan.{OrderedPlan, TriSplittedPlan}
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.testkit.DebugProperties
import izumi.distage.testkit.services.dstest.DistageTestRunner._
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.language.CodePosition
import izumi.fundamentals.platform.strings.IzString._
import izumi.fundamentals.reflection.Tags.TagK

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{Duration, FiniteDuration}

class DistageTestRunner[F[_]: TagK]
(
  reporter: TestReporter,
  integrationChecker: IntegrationChecker[F],
  runnerEnvironment: SpecEnvironment,
  tests: Seq[DistageTest[F]],
  isTestSkipException: Throwable => Boolean,
  parallelTests: Boolean,
  parallelEnvs: Boolean,
) {
  def run(): Unit = {
    val groups = tests.groupBy(_.environment)

    val logger = runnerEnvironment.makeLogger()
    val options = runnerEnvironment.planningOptions
    val loader = runnerEnvironment.makeConfigLoader(logger)

    val config = loader.buildConfig()
    val checker = new PlanCircularDependencyCheck(options, logger)

    logger.info(s"Starting tests across ${groups.size -> "num envs"}, ${groups.values.map(_.size).toList -> "num tests"} in ${TagK[F].tag -> "monad"}")
    debugLogger.log {
      val printedEnvs = groups.toList.map {
        case (t, tests) =>
          final case class TestEnvironment(Activation: Activation, memoizationRoots: DIKey => Boolean, tests: String)
          TestEnvironment(t.activation, t.memoizationRoots, tests.map(_.meta.id).niceList().shift(2))
      }.niceList()
      s"Env contents: $printedEnvs"
    }

    try configuredForeach(groups) {
      case (env, tests) => try {
        // here we scan our classpath to enumerate of our components (we have "bootstrap" components - injector plugins, and app components)
        val provider = runnerEnvironment.makeModuleProvider(options, config, logger, env.roles, env.activationInfo, env.activation)
        val bsModule = provider.bootstrapModules().merge overridenBy env.bsModule overridenBy runnerEnvironment.bootstrapOverrides
        val appModule = provider.appModules().merge overridenBy env.appModule overridenBy runnerEnvironment.moduleOverrides

        val injector = Injector(bsModule)

        // first we need to plan runtime for our monad. Identity is also supported
        val runtimeGcRoots: Set[DIKey] = Set(
          DIKey.get[DIEffectRunner[F]],
          DIKey.get[DIEffect[F]],
          DIKey.get[DIEffectAsync[F]],
        )

        val runtimePlan = injector.plan(PlannerInput(appModule, runtimeGcRoots))

        assert(runtimeGcRoots.diff(runtimePlan.keys).isEmpty)
        // here we plan all the job for each individual test
        val testPlans = tests.map {
          distageTest =>
            val keys = distageTest.test.get.diKeys.toSet
            distageTest -> injector.plan(PlannerInput(appModule, keys))
        }

        val wholeEnvKeys = testPlans.flatMap(_._2.steps).map(_.target).toSet

        // here we find all the shared components in each of our individual tests
        val sharedKeys = wholeEnvKeys.filter(env.memoizationRoots) -- runtimeGcRoots

        logger.info(s"Memoized components in env: $sharedKeys")

        val (strengthenedKeys, strengthenedAppModule) = appModule.drop(runtimeGcRoots).foldLeftWith(List.empty[DIKey]) {
          case (acc, b@SetElementBinding(key, r: ImplDef.ReferenceImpl, _, _)) if r.weak && wholeEnvKeys(key) =>
            (key :: acc) -> b.copy(implementation = r.copy(weak = false))
          case (acc, b) =>
            acc -> b
        }

        if (strengthenedKeys.nonEmpty) {
          logger.info(s"Strengthened weak components in env: $strengthenedKeys")
        }

        val shared = injector.trisectByKeys(strengthenedAppModule, sharedKeys) {
          _.collectChildren[IntegrationCheck].map(_.target).toSet
        }

        checker.verify(runtimePlan)

        debugLogger.log(
          s"""Tests in env: ${tests.size}
             |Integration plan: ${shared.side}
             |Memoized plan: ${shared.shared}
             |App plan: ${shared.primary}
             |""".stripMargin)

        // first we produce our Monad's runtime
        injector.produceF[Identity](runtimePlan).use {
          runtimeLocator =>
            val runner = runtimeLocator.get[DIEffectRunner[F]]
            implicit val F: DIEffect[F] = runtimeLocator.get[DIEffect[F]]
            implicit val P: DIEffectAsync[F] = runtimeLocator.get[DIEffectAsync[F]]

            runner.run {
              // now we produce integration components for our shared plan
              checker.verify(shared.shared)

              F.definitelyRecoverCause {
                Injector.inherit(runtimeLocator).produceF[F](shared.shared).use {
                  sharedLocator =>
                    checker.verify(shared.side)

                    Injector.inherit(sharedLocator).produceF[F](shared.side).use {
                      sharedIntegrationLocator =>
                        ifIntegChecksOk(sharedIntegrationLocator)(tests, shared) {
                          proceed(appModule, checker, testPlans, shared, sharedLocator)
                        }
                    }
                }
              } {
                case (ProvisioningIntegrationException(integrations), _) =>
                  // FIXME: temporary hack to allow missing containers to skip tests (happens when both DockerWrapper & integration check that depends on Docker.Container are memoized)
                  F.maybeSuspend(ignoreIntegrationCheckFailedTests(tests, integrations))
                case (_, cause) =>
                  // fail all tests (if an exception reached here, it must have happened before the individual test runs)
                  F.maybeSuspend(failAllTests(tests, cause))
              }
            }
        }
      } catch {
        case t: Throwable =>
          // fail all tests (if an exception reaches here, it must have happened before the runtime was successfully produced)
          failAllTests(tests, t)
          reporter.onFailure(t)
      }
    } finally reporter.endAll()
  }

  protected def ifIntegChecksOk(integLocator: Locator)(testplans: Seq[DistageTest[F]], plans: TriSplittedPlan)(onSuccess: => F[Unit])(implicit F: DIEffect[F]): F[Unit] = {
    integrationChecker.collectFailures(plans.side.declaredRoots, integLocator).flatMap {
      case Left(failures) =>
        F.maybeSuspend(ignoreIntegrationCheckFailedTests(testplans, failures))

      case Right(_) =>
        onSuccess
    }
  }

  protected def ignoreIntegrationCheckFailedTests(tests: Seq[DistageTest[F]], failures: Seq[ResourceCheck.Failure]): Unit = {
    tests.foreach {
      test =>
        reporter.testStatus(test.meta, TestStatus.Ignored(failures))
    }
  }

  protected def failAllTests(tests: Seq[DistageTest[F]], cause: Throwable): Unit = {
    tests.foreach {
      test =>
        reporter.testStatus(test.meta, TestStatus.Failed(cause, Duration.Zero))
    }
  }

  protected def proceed(appmodule: ModuleBase,
                        checker: PlanCircularDependencyCheck,
                        testplans: Seq[(DistageTest[F], OrderedPlan)],
                        shared: TriSplittedPlan,
                        parent: Locator,
                       )(implicit F: DIEffect[F], P: DIEffectAsync[F]): F[Unit] = {
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
        configuredTraverse_(testsBySuite) {
          case (suiteData, plans) =>
            F.bracket(
              acquire = F.maybeSuspend(reporter.beginSuite(suiteData))
            )(release = _ => F.maybeSuspend(reporter.endSuite(suiteData))) { _ =>
              configuredTraverse_(plans) {
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
                          ifIntegChecksOk(integLocator)(Seq(test), newtestplan) {
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
        case (_, cause) =>
          F.maybeSuspend {
            reporter.testStatus(test.meta, TestStatus.Failed(cause, testDuration(before)))
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

  protected def configuredTraverse_[A](l: Iterable[A])(f: A => F[Unit])(implicit F: DIEffect[F], P: DIEffectAsync[F]): F[Unit] = {
    if (parallelTests) {
      P.parTraverse_(l)(f)
    } else {
      F.traverse_(l)(f)
    }
  }

  protected def configuredForeach[A](environments: Iterable[A])(f: A => Unit): Unit = {
    if (parallelEnvs && environments.size > 1) {
      val ec = ExecutionContext.fromExecutorService(null)
      try {
        DIEffectAsync.diEffectParIdentity.parTraverse_(environments)(f)
      } finally ec.shutdown()
    } else {
      environments.foreach(f)
    }
  }

  lazy val debugLogger: TrivialLogger = TrivialLogger.make[DistageTestRunner[F]](DebugProperties.`izumi.distage.testkit.debug`)
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
