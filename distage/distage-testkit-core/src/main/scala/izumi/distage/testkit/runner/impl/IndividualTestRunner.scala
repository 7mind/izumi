package izumi.distage.testkit.runner.impl

import distage.*
import izumi.distage.framework.services.PlanCircularDependencyCheck
import izumi.distage.model.plan.Plan
import izumi.distage.testkit.model.*
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.runner.impl.services.{TestStatusConverter, TestkitLogging, TimedActionF}
import izumi.functional.bio.Exit
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
  class IndividualTestRunnerImpl[F[_]: TagK](
    reporter: TestReporter,
    logging: TestkitLogging,
    statusConverter: TestStatusConverter,
    timed: TimedActionF[F],
    check: PlanCircularDependencyCheck,
    testkitLogger: IzLogger @Id("distage-testkit"),
  )(implicit F: QuasiIO[F]
  ) extends IndividualTestRunner[F] {

    def proceedTest(
      suiteId: ScopeId,
      depth: Int,
      mainSharedLocator: Locator,
      preparedTest: PreparedTest[F],
    ): F[IndividualTestResult] = {
      val test = preparedTest.test
      val meta = test.meta
      val plan = preparedTest.timedPlan.out
      // this is just the last planning time, not total one
      val successfulPlanningTime = preparedTest.timedPlan.timing

      for {
        _ <- logTest(testkitLogger, test, plan)
        _ <- F.maybeSuspend(check.showProxyWarnings(plan))
        _ <- F.maybeSuspend(
          reporter.testStatus(
            suiteId,
            depth,
            meta,
            TestStatus.Instantiating(plan, successfulPlanningTime, logPlan = (logging.enableDebugOutput || test.environment.debugOutput) && plan.keys.nonEmpty),
          )
        )
        testRunResult <- F.uninterruptibleExcept {
          restore =>
            timed
              .timedLifecycle(Injector.inherit(mainSharedLocator).produceDetailedCustomF[F](plan))
              .use {
                maybeLocator =>
                  maybeLocator.foldEither(
                    {
                      case (f, failedProvTime) =>
                        F.maybeSuspend[IndividualTestResult] {
                          val result = IndividualTestResult.InstantiationFailure(meta, successfulPlanningTime, failedProvTime, f)
                          reporter.testStatus(suiteId, depth, meta, statusConverter.failInstantiation(result))
                          result
                        }
                    },
                    {
                      case (locator, successfulProvTime) =>
                        for {
                          _ <- F.maybeSuspend(reporter.testStatus(suiteId, depth, meta, TestStatus.Running(locator, successfulPlanningTime, successfulProvTime)))
                          successfulTestOutput <- timed.timedWith[Either[(Throwable, Exit.Trace[Throwable]), Unit]] {
                            sampleTiming =>
                              F.definitelyRecoverWithTrace {
                                restore {
                                  locator.run(test.test).map(_ => Right(()): Either[(Throwable, Exit.Trace[Throwable]), Unit])
                                }.guaranteeOnInterrupt {
                                  trace =>
                                    sampleTiming().flatMap {
                                      interruptedExecTime =>
                                        F.maybeSuspend {
                                          val exception = trace.unsafeAttachTraceOrReturnNewThrowable()
                                          val result =
                                            IndividualTestResult
                                              .ExecutionFailure(meta, successfulPlanningTime, successfulProvTime, interruptedExecTime, exception, trace)
                                          reporter.testStatus(suiteId, depth, meta, statusConverter.failExecution(result))
                                        }
                                    }
                                }
                              }(recoverWithTrace = (error, trace) => F.pure(Left((error, trace))))
                          }
                          executionResult <- successfulTestOutput
                            .foldEither(
                              {
                                case ((exception, trace), failedExecTime) =>
                                  F.maybeSuspend[IndividualTestResult] {
                                    val result =
                                      IndividualTestResult.ExecutionFailure(meta, successfulPlanningTime, successfulProvTime, failedExecTime, exception, trace)
                                    reporter.testStatus(suiteId, depth, meta, statusConverter.failExecution(result))
                                    result
                                  }
                              },
                              {
                                case (_, testTiming) =>
                                  F.maybeSuspend[IndividualTestResult] {
                                    val result = IndividualTestResult.TestSuccess(meta, successfulPlanningTime, successfulProvTime, testTiming)
                                    reporter.testStatus(suiteId, depth, meta, statusConverter.success(result))
                                    result
                                  }
                              },
                            )
                        } yield {
                          executionResult
                        }
                    },
                  )
              }
        }
      } yield {
        testRunResult
      }
    }

    private def logTest(testRunnerLogger: IzLogger, test: DistageTest[F], p: Plan): F[Unit] = F.maybeSuspend {
      val testLogger = testRunnerLogger("testId" -> test.meta.test.id)
      testLogger.log(logging.testkitDebugMessagesLogLevel(test.environment.debugOutput))(
        s"""Running test...
           |
           |Test plan: $p""".stripMargin
      )
    }
  }

}
