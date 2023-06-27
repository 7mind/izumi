package izumi.distage.testkit.model

import izumi.distage.model.provisioning.PlanInterpreter.FailedProvision
import izumi.distage.testkit.runner.impl.services.Timing
import izumi.functional.bio.Exit

import scala.concurrent.duration.FiniteDuration

sealed trait IndividualTestResult {
  def totalTime: FiniteDuration
  def test: FullMeta
}

object IndividualTestResult {
  sealed trait IndividualTestFailure extends IndividualTestResult

  case class InstantiationFailure(test: FullMeta, planningTiming: Timing, failedInstantiationTiming: Timing, failure: FailedProvision) extends IndividualTestFailure {
    override def totalTime: FiniteDuration = planningTiming.duration + failedInstantiationTiming.duration
  }

  case class ExecutionFailure(test: FullMeta, planningTiming: Timing, instantiationTiming: Timing, failedExecTiming: Timing, failure: Throwable, trace: Exit.Trace[Throwable])
    extends IndividualTestFailure {
    override def totalTime: FiniteDuration = planningTiming.duration + instantiationTiming.duration + failedExecTiming.duration
  }

  sealed trait IndividualTestSuccess extends IndividualTestResult

  case class TestSuccess(test: FullMeta, planningTiming: Timing, instantiationTiming: Timing, executionTiming: Timing) extends IndividualTestSuccess {
    override def totalTime: FiniteDuration = planningTiming.duration + instantiationTiming.duration + executionTiming.duration
  }
}
