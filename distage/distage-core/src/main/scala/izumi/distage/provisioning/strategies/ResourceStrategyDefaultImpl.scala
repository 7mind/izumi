package izumi.distage.provisioning.strategies

import izumi.distage.model.definition.DIResource.DIResourceBase
import izumi.distage.model.effect.DIEffect
import izumi.distage.model.effect.DIEffect.syntax._
import izumi.distage.model.exceptions.{IncompatibleEffectTypesException, MissingRefException}
import izumi.distage.model.plan.ExecutableOp.MonadicOp
import izumi.distage.model.provisioning.strategies.ResourceStrategy
import izumi.distage.model.provisioning.{NewObjectOp, OperationExecutor, ProvisioningKeyProvider}
import izumi.distage.model.reflection.SafeType
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.unused
import izumi.reflect.TagK

class ResourceStrategyDefaultImpl extends ResourceStrategy {

  override def allocateResource[F[_]: TagK](
    context: ProvisioningKeyProvider,
    @unused executor: OperationExecutor,
    op: MonadicOp.AllocateResource,
  )(implicit F: DIEffect[F]
  ): F[Seq[NewObjectOp]] = {
    val provisionerEffectType = SafeType.getK[F]
    val actionEffectType = op.effectHKTypeCtor

    val isEffect = actionEffectType != SafeType.identityEffectType
    if (isEffect && !(actionEffectType <:< provisionerEffectType)) {
      throw new IncompatibleEffectTypesException(provisionerEffectType, actionEffectType)
    }

    val resourceKey = op.effectKey
    context.fetchKey(resourceKey, makeByName = false) match {
      case Some(resource0) if isEffect =>
        val resource = resource0.asInstanceOf[DIResourceBase[F, Any]]
        resource.acquire.map {
          innerResource =>
            Seq(NewObjectOp.NewResource[F](op.target, resource.extract(innerResource), () => resource.release(innerResource)))
        }
      case Some(resourceSimple) =>
        val resource = resourceSimple.asInstanceOf[DIResourceBase[Identity, Any]]
        F.maybeSuspend(resource.acquire).map {
          innerResource =>
            Seq(NewObjectOp.NewResource[F](op.target, resource.extract(innerResource), () => F.maybeSuspend(resource.release(innerResource))))
        }
      case None =>
        throw new MissingRefException(s"Failed to fetch DIResource instance element $resourceKey", Set(resourceKey), None)
    }
  }

}
