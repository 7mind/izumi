package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.definition.DIResource.DIResourceBase
import com.github.pshirshov.izumi.distage.model.exceptions.{IncompatibleEffectTypesException, UnexpectedProvisionResultException}
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect.syntax._
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.MonadicOp
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.MonadicOp.AllocateResource
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.ResourceStrategy
import com.github.pshirshov.izumi.distage.model.provisioning.{NewObjectOp, OperationExecutor, ProvisioningKeyProvider}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.{SafeType, TagK, identityEffectType}
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity

class ResourceStrategyDefaultImpl
  extends ResourceStrategy {

  override def allocateResource[F[_]: TagK](
                                             context: ProvisioningKeyProvider
                                           , executor: OperationExecutor
                                           , op: MonadicOp.AllocateResource
                                           )(implicit F: DIEffect[F]): F[Seq[NewObjectOp.NewResource[F]]] = {
    val provisionerEffectType = SafeType.getK[F]
    val actionEffectType = op.wiring.effectHKTypeCtor

    val isEffect = actionEffectType != identityEffectType
    if (isEffect && !(actionEffectType <:< provisionerEffectType)) {
      throw new IncompatibleEffectTypesException(provisionerEffectType, actionEffectType)
    }

    val AllocateResource(target, actionOp, _, _) = op

    executor.execute(context, actionOp)
      .flatMap(_.toList match {
        case NewObjectOp.NewInstance(_, resource0) :: Nil if isEffect =>
          val resource = resource0.asInstanceOf[DIResourceBase[F, Any]]
          resource.acquire.map {
            innerResource =>
              Seq(NewObjectOp.NewResource[F](target, resource.extract(innerResource), () => resource.release(innerResource)))
          }
        case NewObjectOp.NewInstance(_, resourceSimple) :: Nil =>
          val resource = resourceSimple.asInstanceOf[DIResourceBase[Identity, Any]]
          F.maybeSuspend(resource.acquire).map {
            innerResource =>
              Seq(NewObjectOp.NewResource[F](target, resource.extract(innerResource), () => F.maybeSuspend(resource.release(innerResource))))
          }
        case r =>
          throw new UnexpectedProvisionResultException(s"Unexpected operation result for ${actionOp.target}: $r, expected a single NewInstance!", r)
      })
  }

}
