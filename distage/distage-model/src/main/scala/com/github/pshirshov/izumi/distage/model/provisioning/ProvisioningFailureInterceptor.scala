package com.github.pshirshov.izumi.distage.model.provisioning

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.exceptions.ProvisioningException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

import scala.language.higherKinds
import scala.util.{Failure, Try}

case class OperationWithResult[V[_] <: Try[_]](op: ExecutableOp, result: V[Seq[OpResult]])

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
  def onImportsFailed(context: ProvisioningMassFailureContext, failures: Seq[OperationWithResult[Failure]], exceptions: Seq[OperationFailed]): Unit

  def onStepOperationFailure(context: ProvisioningFailureContext, result: OpResult, f: Throwable): ProvisionActive

  def onStepFailure(context: ProvisioningFailureContext, f: Throwable): ProvisionActive

  def onBadResult(context: ProvisioningFailureContext): PartialFunction[Throwable, Try[Unit]]

  def onExecutionFailed(context: ProvisioningFailureContext): PartialFunction[Throwable, Try[Seq[OpResult]]]

}

class ProvisioningFailureInterceptorDefaultImpl extends ProvisioningFailureInterceptor {

  override def onImportsFailed(context: ProvisioningMassFailureContext, failures: Seq[OperationWithResult[Failure]], exceptions: Seq[OperationFailed]): Unit = {
    val allFailures = failures.collect {
      case OperationWithResult(op, Failure(f)) =>
        OperationFailed(op, f)
    } ++ exceptions

    val repr = allFailures.map {
      case OperationFailed(op, f) =>
        op.origin match {
          case Some(origin) =>
            s"$origin / ${op.target}: ${f.getMessage}"
          case None =>
            s"${op.target}: ${f.getMessage}"
        }
    }

    throw new ProvisioningException(s"Imports failed: ${repr.niceList()}", allFailures.head.result)
  }

  override def onStepFailure(context: ProvisioningFailureContext, f: Throwable): ProvisionActive = {
    throw new ProvisioningException(s"Step execution failed for ${context.step}", f)
  }

  override def onStepOperationFailure(context: ProvisioningFailureContext, result: OpResult, f: Throwable): ProvisionActive = {
    throw new ProvisioningException(s"Provisioning unexpectedly failed on result handling for result $result of step ${context.step}", f)
  }

  override def onBadResult(context: ProvisioningFailureContext): PartialFunction[Throwable, Try[Unit]] = PartialFunction.empty

  override def onExecutionFailed(context: ProvisioningFailureContext): PartialFunction[Throwable, Try[Seq[OpResult]]] = PartialFunction.empty

}
