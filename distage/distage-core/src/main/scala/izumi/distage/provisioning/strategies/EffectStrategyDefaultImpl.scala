package izumi.distage.provisioning.strategies

import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.effect.QuasiIO.syntax.*
import izumi.distage.model.exceptions.interpretation.ProvisionerIssue
import izumi.distage.model.exceptions.interpretation.ProvisionerIssue.MissingRef
import izumi.distage.model.plan.ExecutableOp.MonadicOp
import izumi.distage.model.provisioning.strategies.EffectStrategy
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}
import izumi.reflect.TagK

class EffectStrategyDefaultImpl extends EffectStrategy {

  override def executeEffect[F[_]: TagK](
    context: ProvisioningKeyProvider,
    op: MonadicOp.ExecuteEffect,
  )(implicit F: QuasiIO[F]
  ): F[Either[ProvisionerIssue, Seq[NewObjectOp]]] = {
    op.throwOnIncompatibleEffectType[F]() match {
      case Left(value) =>
        F.pure(Left(value))
      case Right(_) =>
        val effectKey = op.effectKey
        context.fetchKey(effectKey, makeByName = false) match {
          case Some(action0) if op.isEffect[F] =>
            val action = action0.asInstanceOf[F[Any]]
            action.map(newInstance => Right(Seq(NewObjectOp.NewInstance(op.target, op.instanceTpe, newInstance))))
          case Some(newInstance) =>
            F.pure(Right(Seq(NewObjectOp.NewInstance(op.target, op.instanceTpe, newInstance))))
          case None =>
            F.pure(Left(MissingRef(op.target, "Failed to fetch an effect to execute", Set(effectKey))))
        }
    }
  }

}
