package izumi.distage.provisioning.strategies

import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.effect.QuasiIO.syntax.*
import izumi.distage.model.exceptions.interpretation.MissingRefException
import izumi.distage.model.plan.ExecutableOp.MonadicOp
import izumi.distage.model.provisioning.strategies.EffectStrategy
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}
import izumi.reflect.TagK

class EffectStrategyDefaultImpl extends EffectStrategy {

  override def executeEffect[F[_]: TagK](
    context: ProvisioningKeyProvider,
    op: MonadicOp.ExecuteEffect,
  )(implicit F: QuasiIO[F]
  ): F[Seq[NewObjectOp]] = {
    op.throwOnIncompatibleEffectType[F]()

    val effectKey = op.effectKey
    context.fetchKey(effectKey, makeByName = false) match {
      case Some(action0) if op.isEffect[F] =>
        val action = action0.asInstanceOf[F[Any]]
        action.map(newInstance => Seq(NewObjectOp.NewInstance(op.target, op.instanceType, newInstance)))
      case Some(newInstance) =>
        F.pure(Seq(NewObjectOp.NewInstance(op.target, op.instanceType, newInstance)))
      case None =>
        throw new MissingRefException(s"Failed to fetch an effect to execute: $effectKey", Set(effectKey), None)
    }
  }

}
