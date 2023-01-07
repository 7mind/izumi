package izumi.distage.provisioning.strategies

import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.plan.ExecutableOp.WiringOp
import izumi.distage.model.provisioning.strategies.ProviderStrategy
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}
import izumi.distage.model.reflection.TypedRef

class ProviderStrategyDefaultImpl extends ProviderStrategy {
  def callProvider[F[_]](
    context: ProvisioningKeyProvider,
    op: WiringOp.CallProvider,
  )(implicit F: QuasiIO[F]
  ): F[Either[ProvisionerIssue, Seq[NewObjectOp]]] = {
    import izumi.functional.IzEither.*

    val args = op.wiring.associations.map {
      param =>
        context.fetchKey(param.key, param.isByName) match {
          case Some(dep) =>
            Right(TypedRef(dep, param.key.tpe, param.isByName))
          case _ =>
            Left(List(param))
        }
    }.biAggregate

    args match {
      case Left(value) =>
        F.pure(Left(ProvisionerIssue.UninitializedDependency(op.target, value)))
      case Right(value) =>
        val instance = op.wiring.provider.unsafeApply(value)
        F.pure(Right(Seq(NewObjectOp.NewInstance(op.target, op.instanceType, instance))))
    }
  }
}
