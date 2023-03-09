package izumi.distage.testkit.runner.impl

import distage.*
import izumi.distage.framework.services.PlanCircularDependencyCheck
import izumi.distage.model.exceptions.planning.InjectorFailed
import izumi.distage.model.plan.Plan
import izumi.distage.modules.DefaultModule
import izumi.distage.testkit.model.*
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.runner.impl.TestPlanner.*
import izumi.distage.testkit.runner.impl.services.{TestStatusConverter, TestkitLogging, Timed, TimedAction}
import izumi.functional.quasi.QuasiIO
import izumi.functional.quasi.QuasiIO.syntax.*
import izumi.logstage.api.IzLogger

trait IndividualTestRunner[F[_]] {
  def proceedTest(
    suiteId: ScopeId,
    depth: Int,
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
      suiteId: ScopeId,
      depth: Int,
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
                  .timed(testInjector.produceDetailedCustomF[F](plan))
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