package izumi.distage.testkit.services.dstest

import java.util.concurrent.TimeUnit

import izumi.distage.model.monadic.DIEffect.syntax._
import izumi.distage.model.monadic.{DIEffect, DIEffectRunner}
import izumi.distage.model.plan.{ExecutableOp, OrderedPlan}
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.{TagK, _}
import izumi.distage.model.{Locator, SplittedPlan}
import izumi.distage.roles.model.IntegrationCheck
import izumi.distage.roles.services.{IntegrationChecker, PlanCircularDependencyCheck}
import izumi.distage.testkit.services.dstest.DistageTestRunner.{DistageTest, TestReporter}
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.jvm.CodePosition
import distage.{DIKey, Injector, PlannerInput}

import scala.concurrent.duration.FiniteDuration

// a marker trait just for our demo purposes. All the entities inheriting this trait will be shared between tests contexts
trait TODOMemoizeMe {}



class DistageTestRunner[F[_] : TagK](
                                      reporter: TestReporter,
                                      integrationChecker: IntegrationChecker,
                                      runnerEnvironment: DistageTestEnvironmentImpl[F],
                                      tests: Seq[DistageTest[F]]
                                    ) {

  import DistageTestRunner._

  def run(): Unit = {
    val groups = tests.groupBy(_.environment)

    groups.foreach {
      case (env, group) =>
        val logger = runnerEnvironment.makeLogger()
        val options = runnerEnvironment.contextOptions()
        val loader = runnerEnvironment.makeConfigLoader(logger)
        val config = loader.buildConfig()
        val checker = new PlanCircularDependencyCheck(options, logger)

        // here we scan our classpath to enumerate of our components (we have "bootstrap" components - injector plugins, and app components)
        val provider = runnerEnvironment.makeModuleProvider(options, config, logger, env.roles, env.activation)
        val bsModule = provider.bootstrapModules().merge overridenBy env.bsModule overridenBy runnerEnvironment.bootstrapOverride
        val appModule: distage.Module = provider.appModules().merge overridenBy env.appModule overridenBy runnerEnvironment.appOverride

        val injector = Injector.Standard(bsModule)

        // first we need to plan runtime for our monad. Identity is also supported
        val runtimeGcRoots: Set[DIKey] = Set(
          DIKey.get[DIEffectRunner[F]],
          DIKey.get[DIEffect[F]],
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
            plan.steps.filter(op => ExecutableOp.instanceType(op) weak_<:< SafeType.get[TODOMemoizeMe]).map(_.target)
        }.toSet -- runtimeGcRoots

        val shared = injector.splitPlan(appModule.drop(runtimeGcRoots), sharedKeys) {
          merged =>
            merged.collectChildren[IntegrationCheck].map(_.target).toSet
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
            implicit val effect: DIEffect[F] = runtimeLocator.get[DIEffect[F]]

            runner.run {
              // now we produce integration components for our shared plan
              Injector.inherit(runtimeLocator).produceF[F](shared.subplan).use {
                sharedIntegrationLocator =>
                  check(testplans.map(_._1), shared, effect, sharedIntegrationLocator) {
                    proceed(checker, testplans, shared, sharedIntegrationLocator)
                  }
              }
            }
        }
    }
  }

  private def check(testplans: Seq[DistageTest[F]], plans: SplittedPlan, effect: DIEffect[F], integLocator: Locator)(f: => F[Unit]): F[Unit] = {
    integrationChecker.check(plans.subRoots, integLocator) match {
      case Some(value) =>
        effect.traverse_(testplans) {
          test =>

            effect.maybeSuspend(reporter.testStatus(test.meta, TestStatus.Cancelled(value)))
        }

      case None =>
        f
    }
  }

  private def proceed(checker: PlanCircularDependencyCheck, testplans: Seq[(DistageTest[F], OrderedPlan)], shared: SplittedPlan, sharedIntegrationLocator: Locator)(implicit effect: DIEffect[F]): F[Unit] = {
    // here we produce our shared plan
    checker.verify(shared.primary)
    checker.verify(shared.subplan)
    Injector.inherit(sharedIntegrationLocator).produceF[F](shared.primary).use {
      sharedLocator =>
        val testInjector = Injector.inherit(sharedLocator)

        // now we are ready to run each individual test
        // note: scheduling here is custom also and tests may automatically run in parallel for any non-trivial monad
        effect.traverse_(testplans.groupBy {
          t =>
            val id = t._1.meta.id
            SuiteData(id.suiteName, id.suiteId, id.suiteClassName)
        }) {
          case (id, plans) =>
            for {
              _ <- effect.maybeSuspend(reporter.beginSuite(id))
              _ <- effect.traverse_(plans) {
                case (test, testplan) =>
                  val allSharedKeys = sharedLocator.allInstances.map(_.key).toSet

                  val newtestplan = testInjector.splitExistingPlan(shared.reducedModule.drop(allSharedKeys), testplan.keys -- allSharedKeys, testplan) {
                    _.collectChildren[IntegrationCheck].map(_.target).toSet -- allSharedKeys
                  }

                  checker.verify(newtestplan.subplan)
                  checker.verify(newtestplan.primary)
                  // we are ready to run the test, finally
                  testInjector.produceF[F](newtestplan.subplan).use {
                    integLocator =>
                      check(Seq(test), newtestplan, effect, integLocator) {
                        proceedIndividual(test, newtestplan, integLocator)
                      }
                  }
              }
              _ <- effect.maybeSuspend(reporter.endSuite(id))
            } yield {
            }

        }
    }
  }

  private def proceedIndividual(test: DistageTest[F], newtestplan: SplittedPlan, integLocator: Locator)(implicit effect: DIEffect[F]): F[Unit] = {
    Injector.inherit(integLocator).produceF[F](newtestplan.primary).use {
      testLocator =>
        def doRun(before: Long): F[Unit] = for {

          _ <- testLocator.run(test.test)
          after <- effect.maybeSuspend(System.nanoTime())
          _ <- effect.maybeSuspend(reporter.testStatus(test.meta, TestStatus.Succeed(FiniteDuration(after - before, TimeUnit.NANOSECONDS))))
        } yield {
        }

        def doRecover(before: Long): PartialFunction[Throwable, F[Unit]] = {
          // TODO: here we may also ignore individual tests
          case t: Throwable =>
            val after = System.nanoTime()
            reporter.testStatus(test.meta, TestStatus.Failed(t, FiniteDuration(after - before, TimeUnit.NANOSECONDS)))
            effect.pure(())
        }

        for {
          before <- effect.maybeSuspend(System.nanoTime())
          _ <- effect.maybeSuspend(reporter.testStatus(test.meta, TestStatus.Running))
          _ <- effect.definitelyRecover(doRun(before), doRecover(before))
        } yield {

        }

    }
  }


}

object DistageTestRunner {

  case class TestId(name: String, suiteName: String, suiteId: String, suiteClassName: String)

  case class DistageTest[F[_]](test: ProviderMagnet[F[_]], environment: TestEnvironment, meta: TestMeta)

  case class TestMeta(id: TestId, pos: CodePosition)

  sealed trait TestStatus

  object TestStatus {

    case object Scheduled extends TestStatus

    case object Running extends TestStatus

    case class Cancelled(checks: Seq[ResourceCheck.Failure]) extends TestStatus

    case class Succeed(duration: FiniteDuration) extends TestStatus

    case class Failed(t: Throwable, duration: FiniteDuration) extends TestStatus
  }

  case class SuiteData(suiteName: String, suiteId: String, suiteClassName: String)

  trait TestReporter {
    def beginSuite(id: SuiteData): Unit
    def endSuite(id: SuiteData): Unit
    def testStatus(test: TestMeta, testStatus: TestStatus): Unit
  }

}
