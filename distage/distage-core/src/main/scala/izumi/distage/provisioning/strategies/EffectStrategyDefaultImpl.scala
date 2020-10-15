package izumi.distage.provisioning.strategies

import izumi.distage.model.effect.QuasiEffect
import izumi.distage.model.effect.QuasiEffect.syntax._
import izumi.distage.model.exceptions.{IncompatibleEffectTypesException, MissingRefException}
import izumi.distage.model.plan.ExecutableOp.MonadicOp
import izumi.distage.model.provisioning.strategies.EffectStrategy
import izumi.distage.model.provisioning.{NewObjectOp, OperationExecutor, ProvisioningKeyProvider}
import izumi.distage.model.reflection.SafeType
import izumi.fundamentals.platform.language.unused
import izumi.reflect.TagK

class EffectStrategyDefaultImpl extends EffectStrategy {

  override def executeEffect[F[_]: TagK](
    context: ProvisioningKeyProvider,
    @unused executor: OperationExecutor,
    op: MonadicOp.ExecuteEffect,
  )(implicit F: QuasiEffect[F]
  ): F[Seq[NewObjectOp]] = {
    val provisionerEffectType = SafeType.getK[F]
    val actionEffectType = op.effectHKTypeCtor

    val isEffect = actionEffectType != SafeType.identityEffectType
    if (isEffect && !(actionEffectType <:< provisionerEffectType)) {
      throw new IncompatibleEffectTypesException(op, provisionerEffectType, actionEffectType)
    }

    val effectKey = op.effectKey
    context.fetchKey(effectKey, makeByName = false) match {
      case Some(action0) if isEffect =>
        val action = action0.asInstanceOf[F[Any]]
        action.map(newInstance => Seq(NewObjectOp.NewInstance(op.target, newInstance)))
      case Some(newInstance) =>
        F.pure(Seq(NewObjectOp.NewInstance(op.target, newInstance)))
      case None =>
        throw new MissingRefException(s"Failed to fetch an effect to execute: $effectKey", Set(effectKey), None)
    }
  }

}
