package izumi.distage.provisioning.strategies

import izumi.distage.SubcontextImpl
import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.distage.model.definition.errors.ProvisionerIssue.MissingInstance
import izumi.distage.model.plan.ExecutableOp.{AddRecursiveLocatorRef, WiringOp}
import izumi.distage.model.providers.Functoid
import izumi.distage.model.provisioning.strategies.SubcontextStrategy
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}
import izumi.distage.model.recursive.LocatorRef
import izumi.functional.quasi.QuasiIO
import izumi.reflect.TagK

class SubcontextStrategyDefaultImpl extends SubcontextStrategy {
  override def prepareSubcontext[F[_]: TagK](
    context: ProvisioningKeyProvider,
    op: WiringOp.CreateSubcontext,
  )(implicit F: QuasiIO[F]
  ): F[Either[ProvisionerIssue, Seq[NewObjectOp]]] = {
    val locatorKey = AddRecursiveLocatorRef.magicLocatorKey
    context.fetchKey(locatorKey, makeByName = false) match {
      case Some(value) =>
        val locatorRef = value.asInstanceOf[LocatorRef]
        val provider = op.wiring.provider
        val subplan = op.wiring.subplan
        val ctx = SubcontextImpl.empty[Any](op.wiring.externalKeys, locatorRef, subplan, Functoid(provider), op.target)
        F.pure(Right(Seq(NewObjectOp.UseInstance(op.target, ctx))))

      case None =>
        F.pure(Left(MissingInstance(locatorKey)))
    }
  }
}
