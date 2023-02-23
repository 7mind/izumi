package izumi.distage.testkit.runner

import distage.*
import izumi.distage.framework.services.PlanCircularDependencyCheck
import izumi.distage.model.plan.Plan
import izumi.distage.modules.DefaultModule
import izumi.distage.testkit.model.*
import izumi.distage.testkit.runner.TestPlanner.*
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.runner.services.{ReporterBracket, TestkitLogging}
import izumi.functional.quasi.QuasiIO
import izumi.logstage.api.IzLogger

import scala.concurrent.duration.Duration

trait IndividualTestRunner[F[_]] {
  def proceedTest(
    planChecker: PlanCircularDependencyCheck,
    mainSharedLocator: Locator,
    testRunnerLogger: IzLogger,
    groupStrengthenedKeys: Set[DIKey],
    preparedTest: PreparedTest[F],
  ): F[Unit]
}

object IndividualTestRunner {
  class IndividualTestRunnerImpl[F[_]: TagK: DefaultModule](
    reporter: TestReporter,
    logging: TestkitLogging,
    reporterBracket: ReporterBracket[F],
  )(implicit F: QuasiIO[F]
  ) extends IndividualTestRunner[F] {
    def proceedTest(
      planChecker: PlanCircularDependencyCheck,
      mainSharedLocator: Locator,
      testRunnerLogger: IzLogger,
      groupStrengthenedKeys: Set[DIKey],
      preparedTest: PreparedTest[F],
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
          val testLogger = testRunnerLogger("testId" -> test.meta.test.id)
          testLogger.log(logging.testkitDebugMessagesLogLevel(test.environment.debugOutput))(
            s"""Running test...
               |
               |Test plan: $newTestPlan""".stripMargin
          )

          planChecker.showProxyWarnings(newTestPlan)

          if ((logging.enableDebugOutput || test.environment.debugOutput) && testPlan.keys.nonEmpty) {
            reporter.testInfo(test.meta, s"Test plan info: $testPlan")
          }

          proceedIndividual(test, newTestPlan, testInjector)
      }
    }

    protected def proceedIndividual(test: DistageTest[F], testPlan: Plan, testInjector: Injector[F]): F[Unit] = {
      val before = System.nanoTime()
      import QuasiIO.syntax.*

      F.definitelyRecoverCause {
        for {
          _ <- F.maybeSuspend(reporter.testStatus(test.meta, TestStatus.Instantiating))
          _ <- testInjector.produceCustomF[F](testPlan).use {
            testLocator =>
              for {
                _ <- F.maybeSuspend(reporter.testStatus(test.meta, TestStatus.Running))
                _ <- testLocator
                  .run(test.test).map(_ => ())
              } yield {}
          }

          _ <- F.maybeSuspend(reporter.testStatus(test.meta, reporterBracket.done(before)))
        } yield {}
      } {
        (t, trace) =>
          F.maybeSuspend(reporter.testStatus(test.meta, reporterBracket.fail(before)(t, trace)))
      }
    }

  }

}
