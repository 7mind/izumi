package izumi.distage.model.provisioning

import izumi.distage.model.Locator
import izumi.distage.model.plan.ExecutableOp

import scala.util.Try

final case class ProvisioningFailureContext(
  parentContext: Locator,
  provision: Provision[Any],
  step: ExecutableOp,
)

final case class ProvisioningFailure(
  op: ExecutableOp,
  failure: Throwable,
)

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
