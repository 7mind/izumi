package izumi.distage.testkit.runner.impl

import distage.*
import izumi.distage.framework.services.PlanCircularDependencyCheck
import izumi.distage.model.plan.Plan
import izumi.distage.modules.DefaultModule
import izumi.distage.testkit.model.*
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.runner.impl.services.{TestStatusConverter, TestkitLogging, TimedAction}
import izumi.functional.quasi.QuasiIO
import izumi.functional.quasi.QuasiIO.syntax.*
import izumi.logstage.api.IzLogger

trait IndividualTestRunner[F[_]] {
  def proceedTest(
    suiteId: ScopeId,
    depth: Int,
    mainSharedLocator: Locator,
    preparedTest: PreparedTest[F],
  ): F[IndividualTestResult]
}

object IndividualTestRunner {
  class IndividualTestRunnerImpl[F[_]: TagK: DefaultModule](
    reporter: TestReporter,
    logging: TestkitLogging,
    statusConverter: TestStatusConverter[F],
    timedAction: TimedAction[F],
  )(implicit F: QuasiIO[F]
  ) extends IndividualTestRunner[F] {
    def proceedTest(
      suiteId: ScopeId,
      depth: Int,
      mainSharedLocator: Locator,
      preparedTest: PreparedTest[F],
    ): F[IndividualTestResult] = {
      val test = preparedTest.test

      for {
        maybeNewTestPlan <- timedAction.timed {
          F.maybeSuspend {
            if (preparedTest.roots.nonEmpty) {
              // it's important to remember that .plan() would always return the same result regardless of the parent locator!
              Injector.apply().plan(PlannerInput(preparedTest.module, preparedTest.activation, preparedTest.roots)).aggregateErrors
            } else {
              Right(Plan.empty)
            }
          }
        }
        testResult <- maybeNewTestPlan.mapMerge(
          {
            case (f, failedPlanningTime) =>
              for {
                result <- F.pure(IndividualTestResult.PlanningFailure(test.meta, failedPlanningTime, f))
                _ <- F.maybeSuspend(reporter.testStatus(suiteId, depth, test.meta, statusConverter.failPlanning(result)))
              } yield {
                result
              }

          },
          {
            case (plan, successfulPlanningTime) =>
              for {
                _ <- logTest(mainSharedLocator.get[IzLogger]("distage-testkit"), test, plan)
                _ <- F.maybeSuspend(mainSharedLocator.get[PlanCircularDependencyCheck].showProxyWarnings(plan))
                _ <- F.maybeSuspend(
                  reporter.testStatus(
                    suiteId,
                    depth,
                    test.meta,
                    TestStatus.Instantiating(plan, successfulPlanningTime, logPlan = (logging.enableDebugOutput || test.environment.debugOutput) && plan.keys.nonEmpty),
                  )
                )
                testRunResult <- timedAction
                  .timed(Injector.inherit(mainSharedLocator).produceDetailedCustomF[F](plan))
                  .use {
                    maybeLocator =>
                      maybeLocator.mapMerge(
                        {
                          case (f, failedProvTime) =>
                            for {
                              result <- F.pure(IndividualTestResult.InstantiationFailure(test.meta, successfulPlanningTime, failedProvTime, f))
                              _ <- F.maybeSuspend(reporter.testStatus(suiteId, depth, test.meta, statusConverter.failInstantiation(result)))
                            } yield {
                              result
                            }
                        },
                        {
                          case (l, successfulProvTime) =>
                            for {
                              _ <- F.maybeSuspend(reporter.testStatus(suiteId, depth, test.meta, TestStatus.Running(l, successfulPlanningTime, successfulProvTime)))
                              successfulTestOutput <- timedAction.timed {
                                F.definitelyRecover(l.run(test.test).map(_ => Right(()): Either[Throwable, Unit])) {
                                  f =>
                                    F.pure(Left(f))
                                }
                              }
                              executionResult <- successfulTestOutput.mapMerge(
                                {
                                  case (f, failedExecTime) =>
                                    for {
                                      result <- F.pure(IndividualTestResult.ExecutionFailure(test.meta, successfulPlanningTime, successfulProvTime, failedExecTime, f))
                                      _ <- F.maybeSuspend(reporter.testStatus(suiteId, depth, test.meta, statusConverter.failExecution(result)))
                                    } yield {
                                      result
                                    }

                                },
                                {
                                  case (_, testTiming) =>
                                    for {
                                      result <- F.pure(IndividualTestResult.TestSuccess(test.meta, successfulPlanningTime, successfulProvTime, testTiming))
                                      _ <- F.maybeSuspend(reporter.testStatus(suiteId, depth, test.meta, statusConverter.success(result)))
                                    } yield {
                                      result
                                    }

                                },
                              )
                            } yield {
                              executionResult
                            }
                        },
                      ): F[IndividualTestResult]
                  }

              } yield {
                testRunResult
              }

          },
        )

      } yield {
        testResult
      }
    }

    private def logTest(testRunnerLogger: IzLogger, test: DistageTest[F], p: Plan): F[Unit] = F.maybeSuspend {
      val testLogger = testRunnerLogger("testId" -> test.meta.test.id)
      testLogger.log(logging.testkitDebugMessagesLogLevel(test.environment.debugOutput))(
        s"""Running test...
           |
           |Test plan: $p""".stripMargin
      )

      ()
    }
  }

}
