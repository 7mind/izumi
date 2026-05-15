package izumi.distage.provisioning.strategies

import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.functional.bio.IO2
import ProvisionerIssue.MissingRef
import izumi.distage.model.plan.ExecutableOp.MonadicOp
import izumi.distage.model.provisioning.strategies.EffectStrategy
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}
import izumi.reflect.TagKK

class EffectStrategyDefaultImpl extends EffectStrategy {

  override def executeEffect[F[+_, +_]: TagKK](
    context: ProvisioningKeyProvider,
    op: MonadicOp.ExecuteEffect,
  )(implicit F: IO2[F]
  ): F[Throwable, Either[ProvisionerIssue, Seq[NewObjectOp]]] = {
    op.throwOnIncompatibleBifunctorEffectType[F]() match {
      case Left(value) =>
        F.pure(Left(value))
      case Right(_) =>
        val effectKey = op.effectKey
        context.fetchKey(effectKey, makeByName = false) match {
          case Some(action0) if op.isEffect =>
            val action = action0.asInstanceOf[F[Throwable, Any]]
            F.map(action)(newInstance => Right(Seq(NewObjectOp.NewInstance(op.target, op.instanceTpe, newInstance))))
          case Some(newInstance) =>
            F.pure(Right(Seq(NewObjectOp.NewInstance(op.target, op.instanceTpe, newInstance))))
          case None =>
            F.pure(Left(MissingRef(op.target, "Failed to fetch an effect to execute", Set(effectKey))))
        }
    }
  }

}
