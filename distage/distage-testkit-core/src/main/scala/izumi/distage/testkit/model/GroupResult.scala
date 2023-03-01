package izumi.distage.testkit.model

import izumi.distage.model.provisioning.PlanInterpreter.FailedProvision
import izumi.distage.testkit.runner.services.Timing

sealed trait GroupResult

object GroupResult {
  case class EnvLevelFailure(all: Seq[FullMeta], failure: FailedProvision, instantiationTiming: Timing) extends GroupResult

  case class GroupSuccess(outputs: List[IndividualTestResult], instantiationTiming: Timing) extends GroupResult
}
