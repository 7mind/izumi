package izumi.distage.provisioning.strategies

import izumi.distage.model.definition.Lifecycle
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
      throw new IncompatibleEffectTypesException(op, provisionerEffectType, actionEffectType)
    }

    val resourceKey = op.effectKey
    context.fetchKey(resourceKey, makeByName = false) match {
      case Some(resource0) if isEffect =>
        val resource = resource0.asInstanceOf[Lifecycle[F, Any]]
        // FIXME: make uninterruptible / safe register finalizer sooner than now
        resource.acquire.flatMap {
          innerResource =>
            F.suspendF {
              resource.extract(innerResource).fold(identity, F.pure).map {
                instance =>
                  Seq(NewObjectOp.NewResource[F](op.target, instance, () => resource.release(innerResource)))
              }
            }
        }
      case Some(resourceIdentity0) =>
        val resourceIdentity: Lifecycle[Identity, Any] = resourceIdentity0.asInstanceOf[Lifecycle[Identity, Any]]
        // FIXME: make uninterruptible / safe register finalizer sooner than now
        F.maybeSuspend {
          val innerResource = resourceIdentity.acquire
          val instance: Any = resourceIdentity.extract(innerResource).merge
          Seq(NewObjectOp.NewResource[F](op.target, instance, () => F.maybeSuspend(resourceIdentity.release(innerResource))))
        }
      case None =>
        throw new MissingRefException(s"Failed to fetch Lifecycle instance element $resourceKey", Set(resourceKey), None)
    }
  }

}
