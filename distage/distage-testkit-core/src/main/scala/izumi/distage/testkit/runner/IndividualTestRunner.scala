package izumi.distage.testkit.runner

import distage.*
import izumi.distage.framework.services.PlanCircularDependencyCheck
import izumi.distage.model.exceptions.planning.InjectorFailed
import izumi.distage.model.plan.Plan
import izumi.distage.modules.DefaultModule
import izumi.distage.testkit.model.*
import izumi.distage.testkit.runner.TestPlanner.*
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.runner.services.{TestStatusConverter, TestkitLogging, Timed, TimedAction}
import izumi.functional.quasi.QuasiIO
import izumi.functional.quasi.QuasiIO.syntax.*
import izumi.logstage.api.IzLogger

trait IndividualTestRunner[F[_]] {
  def proceedTest(
    mainSharedLocator: Locator,
    groupStrengthenedKeys: Set[DIKey],
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
      mainSharedLocator: Locator,
      groupStrengthenedKeys: Set[DIKey],
      preparedTest: PreparedTest[F],
    ): F[IndividualTestResult] = {
      val testInjector = Injector.inherit(mainSharedLocator)

      val test = preparedTest.test

      for {
        maybeNewTestPlan <- finalPlan(preparedTest, mainSharedLocator, groupStrengthenedKeys, testInjector)
        testResult <- maybeNewTestPlan.mapMerge(
          {
            case (f, failedPlanningTime) =>
              for {
                _ <- F.maybeSuspend(reporter.testStatus(test.meta, TestStatus.Failed(f, failedPlanningTime.duration)))
              } yield {
                IndividualTestResult.PlanningFailure(test.meta, failedPlanningTime, f)
              }

          },
          {
            case (plan, successfulPlanningTime) =>
              for {
                _ <- logTest(mainSharedLocator.get[IzLogger]("distage-testkit"), test, plan)
                _ <- F.maybeSuspend(mainSharedLocator.get[PlanCircularDependencyCheck].showProxyWarnings(plan))
                _ <- F.maybeSuspend(reporter.testStatus(test.meta, TestStatus.Instantiating))
                testRunResult <- timedAction
                  .timed(testInjector.produceDetailedCustomF[F](plan))
                  .use {
                    maybeLocator =>
                      maybeLocator.mapMerge(
                        {
                          case (f, failedProvTime) =>
                            for {
                              _ <- F.maybeSuspend(reporter.testStatus(test.meta, statusConverter.fail(failedProvTime.duration, f.toThrowable)))
                            } yield {
                              IndividualTestResult.InstantiationFailure(test.meta, successfulPlanningTime, failedProvTime, f)
                            }
                        },
                        {
                          case (l, successfulProvTime) =>
                            for {
                              _ <- F.maybeSuspend(reporter.testStatus(test.meta, TestStatus.Running))
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
                                      _ <- F.maybeSuspend(reporter.testStatus(test.meta, statusConverter.fail(failedExecTime.duration, f)))
                                    } yield {
                                      IndividualTestResult.ExecutionFailure(test.meta, successfulPlanningTime, successfulProvTime, failedExecTime, f)
                                    }

                                },
                                {
                                  case (_, testTiming) =>
                                    for {
                                      _ <- F.maybeSuspend(reporter.testStatus(test.meta, TestStatus.Succeed(successfulTestOutput.timing.duration)))
                                    } yield {
                                      IndividualTestResult.TestSuccess(test.meta, successfulPlanningTime, successfulProvTime, testTiming)
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

      if ((logging.enableDebugOutput || test.environment.debugOutput) && p.keys.nonEmpty) {
        reporter.testInfo(test.meta, s"Final test plan info: $p")
      }
      ()
    }

    private def finalPlan(
      prepared: PreparedTest[F],
      mainSharedLocator: Locator,
      groupStrengthenedKeys: Set[DIKey],
      testInjector: Injector[F],
    ): F[Timed[Either[InjectorFailed, Plan]]] = {
      timedAction.timed {
        F.maybeSuspend {
          val PreparedTest(_, appModule, testPlan, activation) = prepared

          val allSharedKeys = mainSharedLocator.allInstances.map(_.key).toSet
          val newAppModule = appModule.drop(allSharedKeys)
          val newRoots = testPlan.keys -- allSharedKeys ++ groupStrengthenedKeys.intersect(newAppModule.keys)
          val maybeNewTestPlan = if (newRoots.nonEmpty) {
            testInjector.plan(PlannerInput(newAppModule, activation, newRoots)).aggregateErrors
          } else {
            Right(Plan.empty)
          }
          maybeNewTestPlan
        }
      }
    }
  }

}
