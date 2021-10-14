package izumi.distage.model.provisioning

import izumi.distage.model.exceptions.ProvisionerIssue
import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.graphs.struct.IncidenceMatrix

import scala.concurrent.duration.FiniteDuration

sealed trait OpStatus
object OpStatus {
  case class Planned() extends OpStatus
  case class Success(time: FiniteDuration) extends OpStatus
  case class Failure(issues: List[ProvisionerIssue], time: FiniteDuration) extends OpStatus
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
