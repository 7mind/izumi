package izumi.distage.provisioning.strategies

import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.functional.bio.IO2
import ProvisionerIssue.MissingInstance
import izumi.distage.model.plan.ExecutableOp.WiringOp
import izumi.distage.model.provisioning.strategies.InstanceStrategy
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}
import izumi.reflect.TagKK

class InstanceStrategyDefaultImpl extends InstanceStrategy {
  def getInstance[F[+_, +_]: TagKK](context: ProvisioningKeyProvider, op: WiringOp.UseInstance)(implicit F: IO2[F]): F[Throwable, Either[ProvisionerIssue, Seq[NewObjectOp]]] = {
    F.pure(Right(Seq(NewObjectOp.NewInstance(op.target, op.instanceType, op.wiring.instance))))
  }
  def getInstance[F[+_, +_]: TagKK](context: ProvisioningKeyProvider, op: WiringOp.ReferenceKey)(implicit F: IO2[F]): F[Throwable, Either[ProvisionerIssue, Seq[NewObjectOp]]] = {
    context.fetchKey(op.wiring.key, makeByName = false) match {
      case Some(value) =>
        F.pure(Right(Seq(NewObjectOp.UseInstance(op.target, value))))

      case None =>
        F.pure(Left(MissingInstance(op.wiring.key)))
    }
  }
}
