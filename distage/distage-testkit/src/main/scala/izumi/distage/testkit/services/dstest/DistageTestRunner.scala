package izumi.distage.testkit.services.dstest

import java.util.concurrent.TimeUnit

import distage.{DIKey, Injector, PlannerInput}
import izumi.distage.model.monadic.DIEffect.syntax._
import izumi.distage.model.monadic.{DIEffect, DIEffectAsync, DIEffectRunner}
import izumi.distage.model.plan.OrderedPlan
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.TagK
import izumi.distage.model.{Locator, SplittedPlan}
import izumi.distage.roles.model.IntegrationCheck
import izumi.distage.roles.services.{IntegrationChecker, PlanCircularDependencyCheck}
import izumi.distage.testkit.services.dstest.DistageTestRunner.{DistageTest, TestReporter}
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.language.CodePosition

import scala.concurrent.duration.FiniteDuration

class DistageTestRunner[F[_] : TagK](
                                      reporter: TestReporter,
                                      integrationChecker: IntegrationChecker,
                                      runnerEnvironment: DistageTestEnvironment[F],
                                      tests: Seq[DistageTest[F]],
                                    ) {

  import DistageTestRunner._

  def run(): Unit = {
    val groups = tests.groupBy(_.environment)

    val logger = runnerEnvironment.makeLogger()
    val options = runnerEnvironment.contextOptions()
    val loader = runnerEnvironment.makeConfigLoader(logger)

    val config = loader.buildConfig()
    val checker = new PlanCircularDependencyCheck(options, logger)

    logger.info(s"Starting tests across ${groups.size -> "num envs"}")
    logger.trace(s"Env contents: ${groups.keys -> "test environments"}")

    groups.foreach {
      case (env, group) =>

        // here we scan our classpath to enumerate of our components (we have "bootstrap" components - injector plugins, and app components)
        val provider = runnerEnvironment.makeModuleProvider(options, config, logger, env.roles, env.activation)
        val bsModule = provider.bootstrapModules().merge overridenBy env.bsModule overridenBy runnerEnvironment.bootstrapOverride
        val appModule: distage.Module = provider.appModules().merge overridenBy env.appModule overridenBy runnerEnvironment.appOverride

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

        val shared = injector.splitPlan(appModule.drop(runtimeGcRoots), sharedKeys) {
          _.collectChildren[IntegrationCheck].map(_.target).toSet
        }

        //        println(shared.primary.render())
        //        println("===")
        //        println(shared.subplan.render())
        //        println("===")

        checker.verify(runtimePlan)

        // first we produce our Monad's runtime
        injector.produceF[Identity](runtimePlan).use {
          runtimeLocator =>
            val runner = runtimeLocator.get[DIEffectRunner[F]]
            implicit val F: DIEffect[F] = runtimeLocator.get[DIEffect[F]]
            implicit val P: DIEffectAsync[F] = runtimeLocator.get[DIEffectAsync[F]]

            runner.run {
              // now we produce integration components for our shared plan
              checker.verify(shared.subplan)
              Injector.inherit(runtimeLocator).produceF[F](shared.subplan).use {
                sharedIntegrationLocator =>
                  check(testplans.map(_._1), shared, F, sharedIntegrationLocator) {
                    proceed(checker, testplans, shared, sharedIntegrationLocator)
                  }
              }
            }
        }
    }
  }

  private def check(testplans: Seq[DistageTest[F]], plans: SplittedPlan, F: DIEffect[F], integLocator: Locator)(onSuccess: => F[Unit]): F[Unit] = {
    integrationChecker.check(plans.subRoots, integLocator) match {
      case Some(value) =>
        F.traverse_(testplans) {
          test =>
            F.maybeSuspend(reporter.testStatus(test.meta, TestStatus.Cancelled(value)))
        }

      case None =>
        onSuccess
    }
  }

  private def proceed(checker: PlanCircularDependencyCheck, testplans: Seq[(DistageTest[F], OrderedPlan)], shared: SplittedPlan, sharedIntegrationLocator: Locator)
                     (implicit F: DIEffect[F], P: DIEffectAsync[F]): F[Unit] = {
    // here we produce our shared plan
    checker.verify(shared.primary)
    Injector.inherit(sharedIntegrationLocator).produceF[F](shared.primary).use {
      sharedLocator =>
        val testInjector = Injector.inherit(sharedLocator)

        // now we are ready to run each individual test
        // note: scheduling here is custom also and tests may automatically run in parallel for any non-trivial monad
        P.parTraverse_(testplans.groupBy {
          t =>
            val id = t._1.meta.id
            SuiteData(id.suiteName, id.suiteId, id.suiteClassName)
        }) {
          case (id, plans) =>
            for {
              _ <- F.maybeSuspend(reporter.beginSuite(id))
              _ <- P.parTraverse_(plans) {
                case (test, testplan) =>
                  val allSharedKeys = sharedLocator.allInstances.map(_.key).toSet

                  val newtestplan = testInjector.splitExistingPlan(shared.reducedModule.drop(allSharedKeys), testplan.keys -- allSharedKeys, allSharedKeys, testplan) {
                    _.collectChildren[IntegrationCheck].map(_.target).toSet -- allSharedKeys
                  }

                  checker.verify(newtestplan.subplan)
                  checker.verify(newtestplan.primary)
                  // we are ready to run the test, finally
                  testInjector.produceF[F](newtestplan.subplan).use {
                    integLocator =>
                      check(Seq(test), newtestplan, F, integLocator) {
                        proceedIndividual(test, newtestplan, integLocator)
                      }
                  }
              }
              _ <- F.maybeSuspend(reporter.endSuite(id))
            } yield ()
        }
    }
  }

  private def proceedIndividual(test: DistageTest[F], newtestplan: SplittedPlan, integLocator: Locator)(implicit F: DIEffect[F]): F[Unit] = {
    Injector.inherit(integLocator).produceF[F](newtestplan.primary).use {
      testLocator =>
        def doRun(before: Long): F[Unit] = {
          for {
            _ <- testLocator.run(test.test)
            _ <- F.maybeSuspend {
              val after = System.nanoTime()
              reporter.testStatus(test.meta, TestStatus.Succeed(FiniteDuration(after - before, TimeUnit.NANOSECONDS)))
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

  final case class TestMeta(id: TestId, pos: CodePosition)

  final case class SuiteData(suiteName: String, suiteId: String, suiteClassName: String)

  sealed trait TestStatus
  object TestStatus {
    case object Scheduled extends TestStatus
    case object Running extends TestStatus
    final case class Cancelled(checks: Seq[ResourceCheck.Failure]) extends TestStatus
    final case class Succeed(duration: FiniteDuration) extends TestStatus
    final case class Failed(t: Throwable, duration: FiniteDuration) extends TestStatus
  }

  trait TestReporter {
    def beginSuite(id: SuiteData): Unit
    def endSuite(id: SuiteData): Unit
    def testStatus(test: TestMeta, testStatus: TestStatus): Unit
  }

}
