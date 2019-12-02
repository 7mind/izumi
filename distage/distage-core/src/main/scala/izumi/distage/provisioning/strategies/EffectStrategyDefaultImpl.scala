package izumi.distage.provisioning.strategies

import izumi.distage.model.exceptions.{IncompatibleEffectTypesException, UnexpectedProvisionResultException}
import izumi.distage.model.effect.DIEffect
import izumi.distage.model.effect.DIEffect.syntax._
import izumi.distage.model.plan.ExecutableOp.MonadicOp
import izumi.distage.model.plan.ExecutableOp.MonadicOp.ExecuteEffect
import izumi.distage.model.provisioning.strategies.EffectStrategy
import izumi.distage.model.provisioning.{NewObjectOp, OperationExecutor, ProvisioningKeyProvider}
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.{SafeType, identityEffectType}
import izumi.fundamentals.reflection.Tags.TagK

class EffectStrategyDefaultImpl
  extends EffectStrategy {

  override def executeEffect[F[_]: TagK](
                                          context: ProvisioningKeyProvider
                                        , executor: OperationExecutor
                                        , op: MonadicOp.ExecuteEffect
                                        )(implicit F: DIEffect[F]): F[Seq[NewObjectOp.NewInstance]] = {
    val provisionerEffectType = SafeType.getK[F]
    val actionEffectType = op.wiring.effectHKTypeCtor

    val isEffect = actionEffectType != identityEffectType
    if (isEffect && !(actionEffectType <:< provisionerEffectType)) {
      throw new IncompatibleEffectTypesException(provisionerEffectType, actionEffectType)
    }

    val ExecuteEffect(target, actionOp, _, _) = op

    executor.execute(context, actionOp)
      .flatMap(_.toList match {
        case NewObjectOp.NewInstance(_, action0) :: Nil if isEffect =>
          val action = action0.asInstanceOf[F[Any]]
          action.map(newInstance => Seq(NewObjectOp.NewInstance(target, newInstance)))
        case NewObjectOp.NewInstance(_, newInstance) :: Nil =>
          F.pure(Seq(NewObjectOp.NewInstance(target, newInstance)))
        case r =>
          throw new UnexpectedProvisionResultException(s"Unexpected operation result for ${actionOp.target}: $r, expected a single NewInstance!", r)
      })
  }

}
