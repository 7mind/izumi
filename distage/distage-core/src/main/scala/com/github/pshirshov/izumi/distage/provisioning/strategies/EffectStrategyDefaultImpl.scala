package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.exceptions.{IncompatibleEffectTypesException, UnexpectedProvisionResultException}
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect.syntax._
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.MonadicOp
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.MonadicOp.ExecuteEffect
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.EffectStrategy
import com.github.pshirshov.izumi.distage.model.provisioning.{NewObjectOp, OperationExecutor, ProvisioningKeyProvider}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.{SafeType, TagK, identityEffectType}

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
