package com.github.pshirshov.izumi.distage.model.provisioning

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp

import scala.util.Try

case class ProvisioningFailureContext(
                                       parentContext: Locator
                                       , active: ProvisionActive
                                       , step: ExecutableOp
                                     )

case class ProvisioningFailure(op: ExecutableOp, failure: Throwable)

trait ProvisioningFailureInterceptor {
  def onBadResult(context: ProvisioningFailureContext): PartialFunction[Throwable, Try[Unit]]

  def onExecutionFailed(context: ProvisioningFailureContext): PartialFunction[Throwable, Try[Seq[ContextAssignment]]]
}

class ProvisioningFailureInterceptorDefaultImpl extends ProvisioningFailureInterceptor {

  override def onBadResult(context: ProvisioningFailureContext): PartialFunction[Throwable, Try[Unit]] = PartialFunction.empty

  override def onExecutionFailed(context: ProvisioningFailureContext): PartialFunction[Throwable, Try[Seq[ContextAssignment]]] = PartialFunction.empty

}
