package izumi.distage.model.definition.errors

import izumi.distage.model.definition.Binding
import izumi.distage.model.exceptions.runtime.IntegrationCheckException
import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.plan.ExecutableOp.{ImportDependency, MonadicOp, ProxyOp}
import izumi.distage.model.provisioning.NewObjectOp
import izumi.distage.model.provisioning.proxies.ProxyProvider.ProxyContext
import izumi.distage.model.reflection.{DIKey, LinkedParameter, SafeType}

sealed trait ProvisionerIssue {
  def key: DIKey
}

object ProvisionerIssue {
  sealed trait ProvisionerExceptionIssue extends ProvisionerIssue {
    def problem: Throwable
  }
  object ProvisionerExceptionIssue {
    final case class UnexpectedStepProvisioning(op: ExecutableOp, problem: Throwable) extends ProvisionerExceptionIssue {
      override def key: DIKey = op.target
    }

    final case class IntegrationCheckFailure(key: DIKey, problem: IntegrationCheckException) extends ProvisionerExceptionIssue

    final case class UnexpectedIntegrationCheck(key: DIKey, problem: Throwable) extends ProvisionerExceptionIssue
  }

  final case class MissingImport(op: ImportDependency, similarSame: Set[Binding], similarSub: Set[Binding]) extends ProvisionerIssue {
    override def key: DIKey = op.target
  }

  final case class IncompatibleEffectTypes(op: MonadicOp, provisionerEffectType: SafeType, actionEffectType: SafeType) extends ProvisionerIssue {
    override def key: DIKey = op.target
  }
  object IncompatibleEffectTypes {
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

  final case class UnexpectedProvisionResult(key: DIKey, results: Seq[NewObjectOp]) extends ProvisionerIssue

  final case class MissingProxyAdapter(key: DIKey, op: ProxyOp) extends ProvisionerIssue

  final case class UninitializedDependency(key: DIKey, parameters: Seq[LinkedParameter]) extends ProvisionerIssue

  final case class IncompatibleEffectType(key: DIKey, effect: SafeType) extends ProvisionerIssue

  final case class MissingRef(key: DIKey, context: String, missing: Set[DIKey]) extends ProvisionerIssue

  final case class DuplicateInstances(key: DIKey) extends ProvisionerIssue

  final case class MissingInstance(key: DIKey) extends ProvisionerIssue

  final case class UnsupportedOp(tpe: SafeType, op: ExecutableOp, context: String) extends ProvisionerIssue {
    override def key: DIKey = op.target
  }

  final case class NoRuntimeClass(
    key: DIKey
  ) extends ProvisionerIssue

  final case class IncompatibleTypes(
    key: DIKey,
    expected: SafeType,
    got: SafeType,
  ) extends ProvisionerIssue

  final case class IncompatibleRuntimeClass(
    key: DIKey,
    got: Class[?],
    clue: String,
  ) extends ProvisionerIssue

  case class ProxyClassloadingFailed(context: ProxyContext, causes: Seq[Throwable]) extends ProvisionerIssue {
    override def key: DIKey = context.op.target
  }

  case class ProxyInstantiationFailed(context: ProxyContext, cause: Throwable) extends ProvisionerIssue {
    override def key: DIKey = context.op.target
  }

  sealed trait ProxyFailureCause
  object ProxyFailureCause {
    case class CantFindStrategyClass(name: String) extends ProxyFailureCause
    case class ProxiesDisabled() extends ProxyFailureCause
  }
  case class ProxyProviderFailingImplCalled(key: DIKey, provider: Any, cause: ProxyFailureCause) extends ProvisionerIssue

  case class ProxyStrategyFailingImplCalled(key: DIKey, strategy: Any) extends ProvisionerIssue

  final case class UnsupportedProxyOp(op: ExecutableOp) extends ProvisionerIssue {
    override def key: DIKey = op.target
  }
}
