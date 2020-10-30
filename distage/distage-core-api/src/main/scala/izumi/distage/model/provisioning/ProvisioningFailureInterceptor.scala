package izumi.distage.model.provisioning

import izumi.distage.model.Locator
import izumi.distage.model.exceptions.ProvisionerIssue
import izumi.distage.model.plan.ExecutableOp

import scala.util.Try

final case class ProvisioningFailureContext(
  parentContext: Locator,
  provision: Provision[Any],
  step: ExecutableOp,
)

sealed trait ProvisioningFailure

final case class AggregateFailure(failures: Seq[ProvisionerIssue]) extends ProvisioningFailure
final case class StepProvisioningFailure(
  op: ExecutableOp,
  failure: Throwable,
) extends ProvisioningFailure

trait ProvisioningFailureInterceptor {
  def onBadResult(context: ProvisioningFailureContext): PartialFunction[Throwable, Try[Unit]]
  def onExecutionFailed(context: ProvisioningFailureContext): PartialFunction[Throwable, Try[Seq[NewObjectOp]]]
}
object ProvisioningFailureInterceptor {
  class DefaultImpl extends ProvisioningFailureInterceptor {
    override def onBadResult(context: ProvisioningFailureContext): PartialFunction[Throwable, Try[Unit]] = PartialFunction.empty
    override def onExecutionFailed(context: ProvisioningFailureContext): PartialFunction[Throwable, Try[Seq[NewObjectOp]]] = PartialFunction.empty
  }
}
