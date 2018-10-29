package com.github.pshirshov.izumi.distage.model.provisioning

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.exceptions.ProvisioningException
import com.github.pshirshov.izumi.distage.model.plan.{ExecutableOp, FormattingUtils, OrderedPlan}
import com.github.pshirshov.izumi.fundamentals.platform.exceptions.IzThrowable._
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

import scala.util.Try

case class ProvisioningFailureContext(
                                       parentContext: Locator
                                       , active: ProvisionActive
                                       , step: ExecutableOp
                                     )

case class ProvisioningMassFailureContext(
                                           parentContext: Locator
                                           , active: ProvisionActive
                                         )
case class ProvisioningFailure(op: ExecutableOp, failure: Throwable)

trait ProvisioningFailureInterceptor {
  def onProvisioningFailed(toImmutable: ProvisionImmutable, plan: OrderedPlan, parentContext: Locator, failures: Seq[ProvisioningFailure]): ProvisionImmutable

  def onBadResult(context: ProvisioningFailureContext): PartialFunction[Throwable, Try[Unit]]

  def onExecutionFailed(context: ProvisioningFailureContext): PartialFunction[Throwable, Try[Seq[OpResult]]]
}

class ProvisioningFailureInterceptorDefaultImpl extends ProvisioningFailureInterceptor {


  override def onProvisioningFailed(toImmutable: ProvisionImmutable, plan: OrderedPlan, parentContext: Locator, failures: Seq[ProvisioningFailure]): ProvisionImmutable = {
    val repr = failures.map {
      case ProvisioningFailure(op, f) =>
        val pos = FormattingUtils.formatBindingPosition(op.origin)
        s"${op.target} $pos, ${f.getClass.getCanonicalName}: ${f.getMessage}"
    }

    val ccFailed = repr.size
    val ccDone = toImmutable.instances.size
    val ccTotal = plan.steps.size

    throw new ProvisioningException(s"Provisioner stopped after $ccDone instances, $ccFailed/$ccTotal operations failed: ${repr.niceList()}", null)
      .addAllSuppressed(failures.map(_.failure))
  }

  override def onBadResult(context: ProvisioningFailureContext): PartialFunction[Throwable, Try[Unit]] = PartialFunction.empty

  override def onExecutionFailed(context: ProvisioningFailureContext): PartialFunction[Throwable, Try[Seq[OpResult]]] = PartialFunction.empty

}
