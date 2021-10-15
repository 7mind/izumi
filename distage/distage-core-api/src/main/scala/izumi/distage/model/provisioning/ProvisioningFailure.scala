package izumi.distage.model.provisioning

import izumi.distage.model.exceptions.ProvisionerIssue
import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.graphs.struct.IncidenceMatrix

import scala.concurrent.duration.FiniteDuration

sealed trait OpStatus
object OpStatus {
  sealed trait Done extends OpStatus {
    def time: FiniteDuration
  }

  case class Planned() extends OpStatus
  case class Success(time: FiniteDuration) extends Done
  case class Failure(issues: List[ProvisionerIssue], time: FiniteDuration) extends Done
}

sealed trait ProvisioningFailure {
  def status: Map[DIKey, OpStatus]
}

object ProvisioningFailure {
  final case class AggregateFailure(graph: IncidenceMatrix[DIKey], failures: Seq[ProvisionerIssue], status: Map[DIKey, OpStatus]) extends ProvisioningFailure

  final case class BrokenGraph(graph: IncidenceMatrix[DIKey], status: Map[DIKey, OpStatus]) extends ProvisioningFailure

//  final case class StepProvisioningFailure(
//    op: ExecutableOp,
//    failure: Throwable,
//  ) extends ProvisioningFailure
}
