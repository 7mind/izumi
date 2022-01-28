package izumi.distage.model.exceptions

import izumi.distage.model.plan.ExecutableOp.{ImportDependency, MonadicOp}
import izumi.distage.model.reflection.SafeType

sealed trait ProvisionerIssue

case class MissingImport(op: ImportDependency) extends ProvisionerIssue

case class IncompatibleEffectTypesException(op: MonadicOp, provisionerEffectType: SafeType, actionEffectType: SafeType) extends ProvisionerIssue

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
