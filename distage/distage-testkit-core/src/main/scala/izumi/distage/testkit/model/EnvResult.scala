package izumi.distage.testkit.model

import izumi.distage.model.provisioning.PlanInterpreter.FailedProvision
import izumi.distage.testkit.runner.impl.services.Timing

sealed trait EnvResult

object EnvResult {
  case class EnvSuccess(timing: Timing, outputs: List[GroupResult]) extends EnvResult

  case class RuntimePlanningFailure(timing: Timing, all: Seq[FullMeta], failure: FailedProvision) extends EnvResult
}
