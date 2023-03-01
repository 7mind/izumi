package izumi.distage.testkit.model

import izumi.distage.model.provisioning.PlanInterpreter.FailedProvision
import izumi.distage.testkit.runner.impl.services.Timing

sealed trait EnvResult

object EnvResult {
  case class EnvSuccess(t: Timing, outputs: List[GroupResult]) extends EnvResult

  case class RuntimePlanningFailure(t: Timing, all: Seq[FullMeta], failure: FailedProvision) extends EnvResult
}
