package com.github.pshirshov.izumi.distage.testkit.services.dstest

import java.util.concurrent.TimeUnit

import cats.~>
import com.github.pshirshov.izumi.distage.model.definition.DIResource
import com.github.pshirshov.izumi.distage.model.definition.DIResource.DIResourceBase
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect.syntax._
import com.github.pshirshov.izumi.distage.model.monadic.{DIEffect, DIEffectRunner}
import com.github.pshirshov.izumi.distage.model.plan.{ExecutableOp, OrderedPlan}
import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.{TagK, _}
import com.github.pshirshov.izumi.distage.model.{Locator, SplittedPlan}
import com.github.pshirshov.izumi.distage.roles.model.IntegrationCheck
import com.github.pshirshov.izumi.distage.roles.services.IntegrationChecker.IntegrationCheckException
import com.github.pshirshov.izumi.distage.roles.services.{IntegrationChecker, PlanCircularDependencyCheck}
import com.github.pshirshov.izumi.distage.testkit.services.dstest.DistageTestRunner.{DistageTest, TestReporter}
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity
import com.github.pshirshov.izumi.fundamentals.platform.integration.ResourceCheck
import com.github.pshirshov.izumi.fundamentals.platform.jvm.CodePosition
import distage.{DIKey, Injector, PlannerInput}

import scala.concurrent.duration.FiniteDuration

// a marker trait just for our demo purposes. All the entities inheriting this trait will be shared between tests contexts
trait TODOMemoizeMe {}



class DistageTestRunner[F[_]: TagK](
                                      reporter: TestReporter,
                                      integrationChecker: IntegrationChecker,
                                      runnerEnvironment: DistageTestEnvironment[F],
                                      tests: Seq[DistageTest[F]]
                                    ) {

  import DistageTestRunner._

  def run(): Unit = {
    resource().foreach(_._2.use {
      case GroupExecutionEnvironmentResource(runner, effect, groupContinuation) =>
        implicit val F: DIEffect[F] = effect
        runner.run(groupContinuation.use(proceedGroup(_)(F.traverse_(_)(identity))))
    })
  }

  def resource(): List[(TestEnvironment, DIResourceBase[Identity, GroupExecutionEnvironmentResource[F]])] = {
    val groups = tests.groupBy(_.environment).toList

    groups.map {
      case (env, group) =>
        val logger = runnerEnvironment.makeLogger()
        val options = runnerEnvironment.contextOptions()
        val loader = runnerEnvironment.makeConfigLoader(logger)
        val config = loader.buildConfig()
        val checker = new PlanCircularDependencyCheck(options, logger)

        // here we scan our classpath to enumerate of our components (we have "bootstrap" components - injector plugins, and app components)
        val provider = runnerEnvironment.makeModuleProvider(options, config, logger, env.roles, env.activation)
        val bsModule = provider.bootstrapModules().merge overridenBy env.bsModule overridenBy runnerEnvironment.bootstrapOverride
        val appModule = provider.appModules().merge overridenBy env.appModule overridenBy runnerEnvironment.appOverride

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
          test =>
            val keys = test.fn.get.diKeys.toSet
            val withUnboundParametersAsRoots = runnerEnvironment.addUnboundParametersAsRoots(keys, appModule)
            test -> injector.plan(PlannerInput(withUnboundParametersAsRoots, keys))
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
        env -> injector.produceF[Identity](runtimePlan).map {
          runtimeLocator =>
            val runner = runtimeLocator.get[DIEffectRunner[F]]
            implicit val effect: DIEffect[F] = runtimeLocator.get[DIEffect[F]]

            // now we produce integration components for our shared plan
            GroupExecutionEnvironmentResource(runner, effect, Injector.inherit(runtimeLocator).produceF[F](shared.subplan).flatMap {
              sharedIntegrationLocator =>
                DIResource.liftF {
                  check(testplans.map(_._1), shared, sharedIntegrationLocator)(
                    fail = effect.fail[DIResourceBase[F, GroupContinuation[F]]](new IntegrationCheckException("Integration check failed", Nil))
                  ) {
                    effect.maybeSuspend(groupContinuationResource(checker, testplans, shared, sharedIntegrationLocator))
                  }
                }.flatMap(identity)
            })
        }
    }
  }

  def check[F[_], A](testplans: Seq[DistageTest[F]], plans: SplittedPlan, integLocator: Locator)
                    (fail: => F[A])(succ: => F[A])
                    (implicit effect: DIEffect[F]): F[A] = {
    integrationChecker.check(plans.subRoots, integLocator) match {
      case Some(value) =>
        effect.traverse_(testplans) {
          test =>
            effect.maybeSuspend(reporter.testStatus(test.meta, TestStatus.Cancelled(value)))
        }.flatMap(_ => fail)

      case None =>
        succ
    }
  }

  def groupContinuationResource(checker: PlanCircularDependencyCheck, testplans: Seq[(DistageTest[F], OrderedPlan)], shared: SplittedPlan, sharedIntegrationLocator: Locator)
                               (implicit effect: DIEffect[F]): DIResourceBase[F, GroupContinuation[F]] = {
    groupLocatorResource(checker, shared, sharedIntegrationLocator)
      .map(GroupContinuation(checker, testplans, shared, _))
  }

  private def groupLocatorResource(checker: PlanCircularDependencyCheck, shared: SplittedPlan, sharedIntegrationLocator: Locator)
                                  (implicit effect: DIEffect[F]): DIResourceBase[F, Locator] = {
    // here we produce our shared plan
    checker.verify(shared.primary)
    checker.verify(shared.subplan)
    Injector.inherit(sharedIntegrationLocator).produceF[F](shared.primary)
  }

  def proceedGroup[F[_]: TagK](groupContinuation: GroupContinuation[F])
                              (sequence: Iterable[F[Unit]] => F[Unit])
                              (implicit effect: DIEffect[F]): F[Unit] = {
    val GroupContinuation(checker, testplans, shared, sharedLocator) = groupContinuation

    val testInjector = Injector.inherit(sharedLocator)

    // now we are ready to run each individual suite of tests
    // note: scheduling here is custom also and tests may automatically run in parallel for any non-trivial monad
    sequence(testplans.groupBy {
      case (test, _) =>
        val id = test.meta.id
        SuiteData(id.suiteName, id.suiteId, id.suiteClassName)
    }.map {
      case (id, plans) =>
        for {
          _ <- effect.maybeSuspend(reporter.beginSuite(id))
          _ <- sequence(plans.map {
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
                  check(Seq(test), newtestplan, integLocator)(fail = effect.unit) {
                    proceedIndividual(test, newtestplan, integLocator)
                  }
              }
          })
          _ <- effect.maybeSuspend(reporter.endSuite(id))
        } yield ()
    })
  }

  def proceedIndividual[F[_]: TagK](test: DistageTest[F], newtestplan: SplittedPlan, integLocator: Locator)
                                   (implicit effect: DIEffect[F]): F[Unit] = {
    Injector.inherit(integLocator).produceF[F](newtestplan.primary).use {
      testLocator =>
        def doRun(before: Long): F[Unit] = for {

          _ <- testLocator.run(test.fn)
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

  case class GroupExecutionEnvironment[F[_]](
                                              effectRunner: DIEffectRunner[F],
                                              effect: DIEffect[F],
                                              groupContinuation: GroupContinuation[F],
                                            )

  case class GroupExecutionEnvironmentResource[F[_]](
                                                      effectRunner: DIEffectRunner[F],
                                                      effect: DIEffect[F],
                                                      groupContinuationResource: DIResourceBase[F, GroupContinuation[F]],
                                                    )

  case class GroupContinuation[F[_]](
                                      checker: PlanCircularDependencyCheck,
                                      testplans: Seq[(DistageTest[F], OrderedPlan)],
                                      shared: SplittedPlan,
                                      sharedLocator: Locator,
                                    ) {
    def mapK[G[_]: TagK](t: F ~> G): GroupContinuation[G] = {
      copy(testplans = testplans.map {
        case (test, plan) =>
          test.copy(fn = test.fn.map(t(_))) -> plan
      })
    }
  }

  case class TestId(name: String, suiteName: String, suiteId: String, suiteClassName: String)

  case class DistageTest[F[_]](fn: ProviderMagnet[F[_]], environment: TestEnvironment, meta: TestMeta)

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
