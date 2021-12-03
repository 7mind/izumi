package izumi.distage.model.exceptions.interpretation

import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.plan.ExecutableOp.{ImportDependency, MonadicOp, ProxyOp}
import izumi.distage.model.provisioning.NewObjectOp
import izumi.distage.model.reflection.{DIKey, SafeType}

sealed trait ProvisionerIssue {
  def key: DIKey
}

case class UnexpectedDIException(key: DIKey, problem: Throwable) extends ProvisionerIssue

case class MissingImport(op: ImportDependency) extends ProvisionerIssue {
  override def key: DIKey = op.target
}

case class IncompatibleEffectTypesException(op: MonadicOp, provisionerEffectType: SafeType, actionEffectType: SafeType) extends ProvisionerIssue {
  override def key: DIKey = op.target
}

case class UnexpectedProvisionResultException(key: DIKey, results: Seq[NewObjectOp]) extends ProvisionerIssue

case class MissingProxyAdapterException(key: DIKey, op: ProxyOp) extends ProvisionerIssue

case class UnsupportedProxyOpException(op: ExecutableOp) extends ProvisionerIssue {
  override def key: DIKey = op.target
}

object IncompatibleEffectTypesException {
  def format(op: MonadicOp, provisionerEffectType: SafeType, actionEffectType: SafeType): String = {
    s"""Incompatible effect types when trying to execute operation:
       |
       |  - $op
       |
       |Can't execute an effect in `$actionEffectType` which is neither equivalent to `izumi.fundamentals.platform.Identity`, nor a subtype of the Injector's effect type: `$provisionerEffectType`
       |
       |  - To execute `make[_].fromEffect` and `make[_].fromResource` bindings for effects other than `Identity`, you must parameterize the `Injector` with the corresponding effect type when creating it, as in `Injector[F]()`.
       |  - Subtype type constructors are allowed. e.g. when using ZIO you can execute effects in `IO[Nothing, _]` when using an `Injector[IO[Throwable, _]]()`.
       """.stripMargin
  }
}