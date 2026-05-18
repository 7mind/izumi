package izumi.distage.testkit.model

import izumi.distage.model.provisioning.PlanInterpreter.FailedProvision
import izumi.distage.testkit.runner.impl.services.Timing
import izumi.functional.bio.Exit

import java.time.OffsetDateTime
import scala.concurrent.duration.FiniteDuration

sealed trait IndividualTestResult {
  def totalTime: FiniteDuration
  def test: FullMeta

  /** The earliest observed phase begin time across this result's phases.
    *
    * Used by reporters to populate the wall-clock timestamp on the
    * test-start side of the corresponding event stream (e.g. `TestStarting`).
    */
  def beginInstant: OffsetDateTime

  /** The latest observed phase end time across this result's phases.
    *
    * Used by reporters to populate the wall-clock timestamp on the
    * test-completion side of the event stream (e.g. `TestSucceeded`, `TestFailed`).
    */
  def endInstant: OffsetDateTime
}

object IndividualTestResult {
  sealed trait IndividualTestFailure extends IndividualTestResult

  case class InstantiationFailure(test: FullMeta, planningTiming: Timing, failedInstantiationTiming: Timing, failure: FailedProvision) extends IndividualTestFailure {
    override def totalTime: FiniteDuration = planningTiming.duration + failedInstantiationTiming.duration
    override def beginInstant: OffsetDateTime = planningTiming.begin
    override def endInstant: OffsetDateTime = failedInstantiationTiming.end
  }

  case class ExecutionFailure(test: FullMeta, planningTiming: Timing, instantiationTiming: Timing, failedExecTiming: Timing, failure: Throwable, trace: Exit.Trace[Throwable])
    extends IndividualTestFailure {
    override def totalTime: FiniteDuration = planningTiming.duration + instantiationTiming.duration + failedExecTiming.duration
    override def beginInstant: OffsetDateTime = planningTiming.begin
    override def endInstant: OffsetDateTime = failedExecTiming.end
  }

  sealed trait IndividualTestSuccess extends IndividualTestResult

  case class TestSuccess(test: FullMeta, planningTiming: Timing, instantiationTiming: Timing, executionTiming: Timing) extends IndividualTestSuccess {
    override def totalTime: FiniteDuration = planningTiming.duration + instantiationTiming.duration + executionTiming.duration
    override def beginInstant: OffsetDateTime = planningTiming.begin
    override def endInstant: OffsetDateTime = executionTiming.end
  }
}
