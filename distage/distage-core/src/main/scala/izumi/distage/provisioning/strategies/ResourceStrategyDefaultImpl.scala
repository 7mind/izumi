package izumi.distage.provisioning.strategies

import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.functional.bio.{Bifunctorized, IO2}
import ProvisionerIssue.MissingRef
import izumi.distage.model.plan.ExecutableOp.MonadicOp
import izumi.distage.model.provisioning.strategies.ResourceStrategy
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}
import izumi.reflect.TagKK

class ResourceStrategyDefaultImpl extends ResourceStrategy {

  override def allocateResource[F[+_, +_]: TagKK](
    context: ProvisioningKeyProvider,
    op: MonadicOp.AllocateResource,
  )(implicit F: IO2[F]
  ): F[Throwable, Either[ProvisionerIssue, Seq[NewObjectOp]]] = {
    op.throwOnIncompatibleBifunctorEffectType[F]() match {
      case Left(value) =>
        F.pure(Left(value))
      case Right(_) =>
        val resourceKey = op.effectKey
        context.fetchKey(resourceKey, makeByName = false) match {
          case Some(resource0) if op.isEffect =>
            val resource = resource0.asInstanceOf[Lifecycle[F, Throwable, Any]]
            // FIXME: make explicitly uninterruptible / save register finalizer sooner than now
            resource.acquire.flatMap {
              innerResource =>
                F.suspendThrowable {
                  resource.extract(innerResource).fold(identity, F.pure[Any]).map {
                    instance =>
                      Right(Seq(NewObjectOp.NewResource[F](op.target, op.instanceTpe, instance, () => resource.release(innerResource))))
                  }
                }
            }
          case Some(resourceIdentity0) =>
            val resourceIdentity: Lifecycle[Bifunctorized.IdentityBifunctorized, Throwable, Any] =
              resourceIdentity0.asInstanceOf[Lifecycle[Bifunctorized.IdentityBifunctorized, Throwable, Any]]
            // FIXME: make explicitly uninterruptible / save register finalizer sooner than now
            F.sync {
              val innerResource = Bifunctorized.debifunctorizeIdentity(resourceIdentity.acquire)
              val instance: Any = Bifunctorized.debifunctorizeIdentity(
                resourceIdentity.extract(innerResource).fold[Bifunctorized.IdentityBifunctorized[Throwable, Any]](identity, Bifunctorized.bifunctorizeIdentity(_))
              )
              Right(
                Seq(
                  NewObjectOp.NewResource[F](
                    op.target,
                    op.instanceTpe,
                    instance,
                    () => F.sync(Bifunctorized.debifunctorizeIdentity(resourceIdentity.release(innerResource))),
                  )
                )
              )
            }
          case None =>
            F.pure(Left(MissingRef(op.target, "Failed to fetch Lifecycle instance element ", Set(resourceKey))))
        }
    }
  }

}
