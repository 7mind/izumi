package izumi.distage.testkit.runner.impl

import distage.*
import izumi.distage.framework.services.PlanCircularDependencyCheck
import izumi.distage.model.plan.Plan
import izumi.distage.testkit.model.*
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.runner.impl.services.{TestStatusConverter, TestkitLogging, TimedActionF}
import izumi.functional.bio.Exit
import izumi.functional.bio.{IO2, Primitives2}
import izumi.logstage.api.IzLogger

trait IndividualTestRunner[F[+_, +_]] {
  def proceedTest(
    suiteId: ScopeId,
    depth: Int,
    mainSharedLocator: Locator,
    preparedTest: PreparedTest[F[Throwable, _]],
  ): F[Throwable, IndividualTestResult]
}

object IndividualTestRunner {
  class IndividualTestRunnerImpl[F[+_, +_]: TagKK](
    reporter: TestReporter,
    logging: TestkitLogging,
    statusConverter: TestStatusConverter,
    timed: TimedActionF[F],
    check: PlanCircularDependencyCheck,
    testkitLogger: IzLogger @Id("distage-testkit"),
  )(implicit F: IO2[F],
    FP: Primitives2[F],
  ) extends IndividualTestRunner[F] {

    def proceedTest(
      suiteId: ScopeId,
      depth: Int,
      mainSharedLocator: Locator,
      preparedTest: PreparedTest[F[Throwable, _]],
    ): F[Throwable, IndividualTestResult] = {
      val test = preparedTest.test
      val meta = test.meta
      val plan = preparedTest.timedPlan.out
      // this is just the last planning time, not total one
      val successfulPlanningTime = preparedTest.timedPlan.timing

      F.flatMap(logTest(testkitLogger, test, plan)) { _ =>
        F.flatMap(F.syncThrowable(check.showProxyWarnings(plan))) { _ =>
          F.flatMap(F.syncThrowable(
            reporter.testStatus(
              suiteId,
              depth,
              meta,
              TestStatus.Instantiating(plan, successfulPlanningTime, logPlan = (logging.enableDebugOutput || test.environment.debugOutput) && plan.keys.nonEmpty),
            )
          )) { _ =>
            F.uninterruptibleExcept[Throwable, IndividualTestResult] { restore =>
              timed
                .timedLifecycle[Throwable, Either[izumi.distage.model.provisioning.PlanInterpreter.FailedProvision, Locator]](Injector.inherit(mainSharedLocator).produceDetailedCustomF[F](plan))
                .use {
                  maybeLocator =>
                    maybeLocator.foldEither(
                      {
                        case (f, failedProvTime) =>
                          F.syncThrowable[IndividualTestResult] {
                            val result = IndividualTestResult.InstantiationFailure(meta, successfulPlanningTime, failedProvTime, f)
                            reporter.testStatus(suiteId, depth, meta, statusConverter.failInstantiation(result))
                            result
                          }
                      },
                      {
                        case (locator, successfulProvTime) =>
                          F.flatMap(F.syncThrowable(reporter.testStatus(suiteId, depth, meta, TestStatus.Running(locator, successfulPlanningTime, successfulProvTime)))) { _ =>
                            F.flatMap(
                              timed.timedWith[Throwable, Either[(Throwable, Exit.Trace[Throwable]), Unit]] {
                                sampleTiming =>
                                  val core: F[Throwable, Either[(Throwable, Exit.Trace[Throwable]), Unit]] =
                                    restore {
                                      F.map(locator.run(test.test).asInstanceOf[F[Throwable, Any]])(_ => Right(()): Either[(Throwable, Exit.Trace[Throwable]), Unit])
                                    }
                                  // Capture both typed Throwable failures and panics (Exit.FailureUninterrupted) and turn
                                  // them into Left for the timing-fold below; interruptions propagate via guaranteeOnInterrupt.
                                  F.sandboxCatchAll[Throwable, Either[(Throwable, Exit.Trace[Throwable]), Unit], Throwable] {
                                    core.guaranteeOnInterrupt {
                                      interruption =>
                                        F.flatMap(sampleTiming()) {
                                          interruptedExecTime =>
                                            F.orTerminate(F.syncThrowable {
                                              val exception = interruption.compoundException
                                              val asTrace: Exit.Trace[Throwable] = interruption.trace
                                              val result =
                                                IndividualTestResult
                                                  .ExecutionFailure(meta, successfulPlanningTime, successfulProvTime, interruptedExecTime, exception, asTrace)
                                              reporter.testStatus(suiteId, depth, meta, statusConverter.failExecution(result))
                                            })
                                        }
                                    }
                                  } {
                                    case Exit.Error(error, trace) => F.pure(Left((error, trace)))
                                    case t: Exit.Termination => F.pure(Left((t.compoundException, t.trace)))
                                  }
                              }
                            ) { successfulTestOutput =>
                              successfulTestOutput
                                .foldEither(
                                  {
                                    case ((exception, trace), failedExecTime) =>
                                      F.syncThrowable[IndividualTestResult] {
                                        val result =
                                          IndividualTestResult.ExecutionFailure(meta, successfulPlanningTime, successfulProvTime, failedExecTime, exception, trace)
                                        reporter.testStatus(suiteId, depth, meta, statusConverter.failExecution(result))
                                        result
                                      }
                                  },
                                  {
                                    case (_, testTiming) =>
                                      F.syncThrowable[IndividualTestResult] {
                                        val result = IndividualTestResult.TestSuccess(meta, successfulPlanningTime, successfulProvTime, testTiming)
                                        reporter.testStatus(suiteId, depth, meta, statusConverter.success(result))
                                        result
                                      }
                                  },
                                )
                            }
                          }
                      },
                    )
                }
            }
          }
        }
      }
    }

    private def logTest(testRunnerLogger: IzLogger, test: DistageTest[F[Throwable, _]], p: Plan): F[Throwable, Unit] = F.syncThrowable {
      val testLogger = testRunnerLogger("testId" -> test.meta.test.id)
      testLogger.log(logging.testkitDebugMessagesLogLevel(test.environment.debugOutput))(
        s"""Running test...
           |
           |Test plan: $p""".stripMargin
      )
    }
  }

}
