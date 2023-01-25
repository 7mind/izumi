package izumi.distage.provisioning.strategies

import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.functional.quasi.QuasiIO
import ProvisionerIssue.MissingInstance
import izumi.distage.model.plan.ExecutableOp.WiringOp
import izumi.distage.model.provisioning.strategies.InstanceStrategy
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}
import izumi.reflect.TagK

class InstanceStrategyDefaultImpl extends InstanceStrategy {
  def getInstance[F[_]: TagK](context: ProvisioningKeyProvider, op: WiringOp.UseInstance)(implicit F: QuasiIO[F]): F[Either[ProvisionerIssue, Seq[NewObjectOp]]] = {
    F.pure(Right(Seq(NewObjectOp.NewInstance(op.target, op.instanceType, op.wiring.instance))))
  }
  def getInstance[F[_]: TagK](context: ProvisioningKeyProvider, op: WiringOp.ReferenceKey)(implicit F: QuasiIO[F]): F[Either[ProvisionerIssue, Seq[NewObjectOp]]] = {
    context.fetchKey(op.wiring.key, makeByName = false) match {
      case Some(value) =>
        F.pure(Right(Seq(NewObjectOp.UseInstance(op.target, value))))

      case None =>
        F.pure(Left(MissingInstance(op.wiring.key)))
    }
  }
}
