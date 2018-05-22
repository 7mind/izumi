package com.github.pshirshov.izumi.distage.model.provisioning

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.exceptions.ProvisioningException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp

import scala.util.Try

case class ProvisioningFailureContext(
                                       parentContext: Locator
                                       , active: ProvisionActive
                                       , step: ExecutableOp
                                     )

trait ProvisioningFailureInterceptor {
  def onStepOperationFailure(context: ProvisioningFailureContext, result: OpResult, f: Throwable): ProvisionActive

  def onStepFailure(context: ProvisioningFailureContext, f: Throwable): ProvisionActive

  def onBadResult(context: ProvisioningFailureContext): PartialFunction[Throwable, Try[Unit]]

  def onExecutionFailed(context: ProvisioningFailureContext): PartialFunction[Throwable, Try[Seq[OpResult]]]

}

class ProvisioningFailureInterceptorDefaultImpl extends ProvisioningFailureInterceptor {
  override def onStepFailure(context: ProvisioningFailureContext, f: Throwable): ProvisionActive = {
    throw new ProvisioningException(s"Step execution failed for ${context.step}", f)
  }

  override def onStepOperationFailure(context: ProvisioningFailureContext, result: OpResult, f: Throwable): ProvisionActive = {
    throw new ProvisioningException(s"Provisioning unexpectedly failed on result handling for result $result of step ${context.step}", f)
  }

  override def onBadResult(context: ProvisioningFailureContext): PartialFunction[Throwable, Try[Unit]] = PartialFunction.empty

  override def onExecutionFailed(context: ProvisioningFailureContext): PartialFunction[Throwable, Try[Seq[OpResult]]] = PartialFunction.empty

}
