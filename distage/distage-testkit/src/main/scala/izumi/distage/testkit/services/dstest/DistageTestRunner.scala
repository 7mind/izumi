package izumi.distage.testkit.services.dstest

import java.util.concurrent.TimeUnit

import distage.{DIKey, Injector, PlannerInput}
import izumi.distage.model.monadic.DIEffect.syntax._
import izumi.distage.model.monadic.{DIEffect, DIEffectAsync, DIEffectRunner}
import izumi.distage.model.plan.{OrderedPlan, TriSplittedPlan}
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.TagK
import izumi.distage.model.Locator
import izumi.distage.roles.model.IntegrationCheck
import izumi.distage.roles.services.{IntegrationChecker, PlanCircularDependencyCheck}
import izumi.distage.testkit.services.dstest.DistageTestRunner.{DistageTest, TestReporter}
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.language.CodePosition

import scala.concurrent.duration.FiniteDuration

class DistageTestRunner[F[_] : TagK]
(
  reporter: TestReporter,
  integrationChecker: IntegrationChecker[F],
  runnerEnvironment: SpecEnvironment[F],
  tests: Seq[DistageTest[F]],
  isTestSkipException: Throwable => Boolean,
) {

  import DistageTestRunner._

  def run(): Unit = {
    val groups = tests.groupBy(_.environment)

    val logger = runnerEnvironment.makeLogger()
    val options = runnerEnvironment.contextOptions
    val loader = runnerEnvironment.makeConfigLoader(logger)

    val config = loader.buildConfig()
    val checker = new PlanCircularDependencyCheck(options, logger)

    logger.info(s"Starting tests across ${groups.size -> "num envs"}")
    logger.trace(s"Env contents: ${groups.keys -> "test environments"}")

    groups.foreach {
      case (env, group) =>

        // here we scan our classpath to enumerate of our components (we have "bootstrap" components - injector plugins, and app components)
        val provider = runnerEnvironment.makeModuleProvider(options, config, logger, env.roles, env.activation)
        val bsModule = provider.bootstrapModules().merge overridenBy env.bsModule overridenBy runnerEnvironment.bootstrapOverrides
        val appModule: distage.Module = provider.appModules().merge overridenBy env.appModule overridenBy runnerEnvironment.moduleOverrides

        val injector = Injector.Standard(bsModule)

        // first we need to plan runtime for our monad. Identity is also supported
        val runtimeGcRoots: Set[DIKey] = Set(
          DIKey.get[DIEffectRunner[F]],
          DIKey.get[DIEffect[F]],
          DIKey.get[DIEffectAsync[F]],
        )

        val runtimePlan = injector.plan(PlannerInput(appModule, runtimeGcRoots))

        assert(runtimeGcRoots.diff(runtimePlan.keys).isEmpty)
        // here we plan all the job for each individual test
        val testplans = group.map {
          pm =>
            val keys = pm.test.get.diKeys.toSet
            val withUnboundParametersAsRoots = runnerEnvironment.addUnboundParametersAsRoots(keys, appModule)
            pm -> injector.plan(PlannerInput(withUnboundParametersAsRoots, keys))
        }

        // here we find all the shared components in each of our individual tests
        val sharedKeys = testplans.map(_._2).flatMap {
          plan =>
            plan.steps.filter(env memoizedKeys _.target).map(_.target)
        }.toSet -- runtimeGcRoots

        logger.info(s"Memoized components in env $sharedKeys")

        val shared = injector.triSplitPlan(appModule.drop(runtimeGcRoots), sharedKeys) {
          _.collectChildren[IntegrationCheck].map(_.target).toSet
        }

        checker.verify(runtimePlan)

        // first we produce our Monad's runtime
        injector.produceF[Identity](runtimePlan).use {
          runtimeLocator =>
            val runner = runtimeLocator.get[DIEffectRunner[F]]
            implicit val F: DIEffect[F] = runtimeLocator.get[DIEffect[F]]
            implicit val P: DIEffectAsync[F] = runtimeLocator.get[DIEffectAsync[F]]

            runner.run {
              // now we produce integration components for our shared plan
              checker.verify(shared.side.plan)

              Injector.inherit(runtimeLocator).produceF[F](shared.shared.plan).use {
                sharedLocator =>

                  Injector.inherit(sharedLocator).produceF[F](shared.side.plan).use {
                    sharedIntegrationLocator =>
                      ifIntegChecksOk(F, sharedIntegrationLocator)(testplans.map(_._1), shared) {
                        proceed(checker, testplans, shared, sharedLocator)
                      }
                  }
              }

            }
        }
    }
  }

  private def ifIntegChecksOk(F: DIEffect[F], integLocator: Locator)(testplans: Seq[DistageTest[F]], plans: TriSplittedPlan)(onSuccess: => F[Unit]): F[Unit] = {
    implicit val FF: DIEffect[F] = F
    integrationChecker.collectFailures(plans.side.roots, integLocator).flatMap {
      case Left(value) =>
        F.traverse_(testplans) {
          test =>
            F.maybeSuspend(reporter.testStatus(test.meta, TestStatus.Ignored(value)))
        }

      case Right(_) =>
        onSuccess

    }
  }

  private def proceed(checker: PlanCircularDependencyCheck, testplans: Seq[(DistageTest[F], OrderedPlan)], shared: TriSplittedPlan, parent: Locator)
                     (implicit F: DIEffect[F], P: DIEffectAsync[F]): F[Unit] = {
    // here we produce our shared plan
    checker.verify(shared.primary.plan)
    Injector.inherit(parent).produceF[F](shared.primary.plan).use {
      mainSharedLocator =>
        val testInjector = Injector.inherit(mainSharedLocator)

        // now we are ready to run each individual test
        // note: scheduling here is custom also and tests may automatically run in parallel for any non-trivial monad
        val tests = P.parTraverse_(testplans.groupBy {
          t =>
            val id = t._1.meta.id
            SuiteData(id.suiteName, id.suiteId, id.suiteClassName)
        }) {
          case (id, plans) =>
            for {
              _ <- F.maybeSuspend(reporter.beginSuite(id))
              _ <- P.parTraverse_(plans) {
                case (test, testplan) =>
                  val allSharedKeys = mainSharedLocator.allInstances.map(_.key).toSet

                  val integrations = testplan.collectChildren[IntegrationCheck].map(_.target).toSet -- allSharedKeys
                  val newtestplan = testInjector.triPlan(shared.primary.module.drop(allSharedKeys), testplan.keys -- allSharedKeys, integrations)

                  checker.verify(newtestplan.primary.plan)
                  checker.verify(newtestplan.side.plan)
                  checker.verify(newtestplan.shared.plan)

                  // we are ready to run the test, finally
                  testInjector.produceF[F](newtestplan.shared.plan).use {
                    sharedLocator =>
                      Injector.inherit(sharedLocator).produceF[F](newtestplan.side.plan).use {
                        integLocator =>
                          ifIntegChecksOk(F, integLocator)(Seq(test), newtestplan) {
                            proceedIndividual(test, newtestplan, sharedLocator)
                          }
                      }
                  }
              }
              _ <- F.maybeSuspend(reporter.endSuite(id))
            } yield ()
        }

        F.definitelyRecover(tests.flatMap(_ => F.maybeSuspend(reporter.endAll()))) {
          f =>
            F.maybeSuspend(reporter.onFailure(f)).flatMap(_ => F.fail(f))
        }

    }
  }


  private def proceedIndividual(test: DistageTest[F], newtestplan: TriSplittedPlan, parent: Locator)(implicit F: DIEffect[F]): F[Unit] = {
    Injector.inherit(parent)
      .produceF[F](newtestplan.primary.plan).use {
      testLocator =>
        def doRun(before: Long): F[Unit] = {
          for {
            _ <- F.definitelyRecover(testLocator.run(test.test).flatMap {
              _ =>
                F.maybeSuspend {
                  val after = System.nanoTime()
                  val testDuration = FiniteDuration(after - before, TimeUnit.NANOSECONDS)
                  reporter.testStatus(test.meta, TestStatus.Succeed(testDuration))
                }
            }) {
              case s if isTestSkipException(s) =>
                F.maybeSuspend {
                  val after = System.nanoTime()
                  val testDuration = FiniteDuration(after - before, TimeUnit.NANOSECONDS)
                  reporter.testStatus(test.meta, TestStatus.Cancelled(s.getMessage, testDuration))
                }
              case o =>
                F.fail(o)
            }
          } yield ()
        }

        def doRecover(before: Long): Throwable => F[Unit] = {
          // TODO: here we may also ignore individual tests
          throwable =>
            F.maybeSuspend {
              val after = System.nanoTime()
              reporter.testStatus(test.meta, TestStatus.Failed(throwable, FiniteDuration(after - before, TimeUnit.NANOSECONDS)))
            }
        }

        for {
          before <- F.maybeSuspend(System.nanoTime())
          _ <- F.maybeSuspend(reporter.testStatus(test.meta, TestStatus.Running))
          _ <- F.definitelyRecoverCause(doRun(before))(doRecover(before))
        } yield ()
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

    case object Scheduled extends TestStatus

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

}
