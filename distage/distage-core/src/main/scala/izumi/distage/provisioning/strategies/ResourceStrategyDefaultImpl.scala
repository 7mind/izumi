package izumi.distage.provisioning.strategies

import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.effect.QuasiIO.syntax.*
import izumi.distage.model.exceptions.interpretation.MissingRefException
import izumi.distage.model.plan.ExecutableOp.MonadicOp
import izumi.distage.model.provisioning.strategies.ResourceStrategy
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}
import izumi.fundamentals.platform.functional.Identity
import izumi.reflect.TagK

class ResourceStrategyDefaultImpl extends ResourceStrategy {

  override def allocateResource[F[_]: TagK](
    context: ProvisioningKeyProvider,
    op: MonadicOp.AllocateResource,
  )(implicit F: QuasiIO[F]
  ): F[Seq[NewObjectOp]] = {
    op.throwOnIncompatibleEffectType[F]()

    val resourceKey = op.effectKey
    context.fetchKey(resourceKey, makeByName = false) match {
      case Some(resource0) if op.isEffect[F] =>
        val resource = resource0.asInstanceOf[Lifecycle[F, Any]]
        // FIXME: make uninterruptible / safe register finalizer sooner than now
        resource.acquire.flatMap {
          innerResource =>
            F.suspendF {
              resource.extract(innerResource).fold(identity, F.pure).map {
                instance =>
                  Seq(NewObjectOp.NewResource[F](op.target, op.instanceTpe, instance, () => resource.release(innerResource)))
              }
            }
        }
      case Some(resourceIdentity0) =>
        val resourceIdentity: Lifecycle[Identity, Any] = resourceIdentity0.asInstanceOf[Lifecycle[Identity, Any]]
        // FIXME: make uninterruptible / safe register finalizer sooner than now
        F.maybeSuspend {
          val innerResource = resourceIdentity.acquire
          val instance: Any = resourceIdentity.extract(innerResource).merge
          Seq(NewObjectOp.NewResource[F](op.target, op.instanceTpe, instance, () => F.maybeSuspend(resourceIdentity.release(innerResource))))
        }
      case None =>
        throw new MissingRefException(s"Failed to fetch Lifecycle instance element $resourceKey", Set(resourceKey), None)
    }
  }

}
