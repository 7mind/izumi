package izumi.distage.model.provisioning

import izumi.distage.model.exceptions.ProvisionerIssue
import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.graphs.struct.IncidenceMatrix

sealed trait ProvisioningFailure

object ProvisioningFailure {
  final case class AggregateFailure(graph: IncidenceMatrix[DIKey], failures: Seq[ProvisionerIssue]) extends ProvisioningFailure

  final case class BrokenGraph(graph: IncidenceMatrix[DIKey]) extends ProvisioningFailure

  //final case class BrokenGraph(graph: IncidenceMatrix[DIKey]) extends ProvisioningFailure

  final case class StepProvisioningFailure(
    op: ExecutableOp,
    failure: Throwable,
  ) extends ProvisioningFailure
}
