package com.github.pshirshov.izumi.distage.model.provisioning

import com.github.pshirshov.izumi.distage.model.exceptions.ProvisioningException
import com.github.pshirshov.izumi.distage.model.plan.{ExecutableOp, FormattingUtils, OrderedPlan}
import com.github.pshirshov.izumi.distage.model.reflection.universe
import com.github.pshirshov.izumi.distage.model.{Locator, reflection}
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

import scala.util.Try

case class OperationFailed(op: ExecutableOp, result: Throwable)

case class ProvisioningFailureContext(
                                       parentContext: Locator
                                       , active: ProvisionActive
                                       , step: ExecutableOp
                                     )

case class ProvisioningMassFailureContext(
                                           parentContext: Locator
                                           , active: ProvisionActive
                                         )

trait ProvisioningFailureInterceptor {
  def onProvisioningFailed(toImmutable: ProvisionImmutable, plan: OrderedPlan, parentContext: Locator, failures: Map[universe.RuntimeDIUniverse.DIKey, Set[Throwable]]): ProvisionImmutable

  def onBadResult(context: ProvisioningFailureContext): PartialFunction[Throwable, Try[Unit]]

  def onExecutionFailed(context: ProvisioningFailureContext): PartialFunction[Throwable, Try[Seq[OpResult]]]
}

class ProvisioningFailureInterceptorDefaultImpl extends ProvisioningFailureInterceptor {


  override def onProvisioningFailed(toImmutable: ProvisionImmutable, plan: OrderedPlan, parentContext: Locator, failures: Map[reflection.universe.RuntimeDIUniverse.DIKey, Set[Throwable]]): ProvisionImmutable = {
    val allFailures = failures.flatMap {
      case (key, fs) =>
        fs.map {
          f =>
            OperationFailed(plan.index(key), f)
        }
    }

    val repr = allFailures.map {
      case OperationFailed(op, f) =>
        val pos = FormattingUtils.formatBindingPosition(op.origin)
        s"${op.target} $pos: ${f.getMessage}"
    }

    throw new ProvisioningException(s"Operations failed (${repr.size}): ${repr.niceList()}", allFailures.head.result)
  }

  override def onBadResult(context: ProvisioningFailureContext): PartialFunction[Throwable, Try[Unit]] = PartialFunction.empty

  override def onExecutionFailed(context: ProvisioningFailureContext): PartialFunction[Throwable, Try[Seq[OpResult]]] = PartialFunction.empty

}
