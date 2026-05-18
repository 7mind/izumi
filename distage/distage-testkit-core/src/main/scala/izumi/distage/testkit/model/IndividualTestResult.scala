package izumi.distage.testkit.model

import izumi.distage.model.provisioning.PlanInterpreter.FailedProvision
import izumi.distage.testkit.runner.impl.services.Timing
import izumi.functional.bio.Exit

sealed trait IndividualTestResult {
  def test: FullMeta
  def testTiming: Timing
}

object IndividualTestResult {
  sealed trait IndividualTestFailure extends IndividualTestResult

  case class InstantiationFailure(test: FullMeta, planningTiming: Timing, failedInstantiationTiming: Timing, failure: FailedProvision) extends IndividualTestFailure {
    override def testTiming: Timing = planningTiming ++ failedInstantiationTiming
  }

  case class ExecutionFailure(test: FullMeta, planningTiming: Timing, instantiationTiming: Timing, failedExecTiming: Timing, failure: Throwable, trace: Exit.Trace[Throwable])
    extends IndividualTestFailure {
    override def testTiming: Timing = planningTiming ++ instantiationTiming ++ failedExecTiming
  }

  sealed trait IndividualTestSuccess extends IndividualTestResult

  case class TestSuccess(test: FullMeta, planningTiming: Timing, instantiationTiming: Timing, executionTiming: Timing) extends IndividualTestSuccess {
    override def testTiming: Timing = planningTiming ++ instantiationTiming ++ executionTiming
  }
}
