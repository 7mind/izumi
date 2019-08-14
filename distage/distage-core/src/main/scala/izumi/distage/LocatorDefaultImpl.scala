package izumi.distage

import izumi.distage.model.Locator
import izumi.distage.model.plan.OrderedPlan
import izumi.distage.model.provisioning.PlanInterpreter
import izumi.distage.model.provisioning.Provision.ProvisionImmutable
import izumi.distage.model.references.IdentifiedRef
import izumi.distage.model.reflection.universe.RuntimeDIUniverse
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.{SafeType, TagK}

final class LocatorDefaultImpl[F[_]]
(
  val plan: OrderedPlan,
  val parent: Option[Locator],
  val dependencyMap: ProvisionImmutable[F],
) extends AbstractLocator {
  override protected def unsafeLookup(key: RuntimeDIUniverse.DIKey): Option[Any] =
    dependencyMap.get(key)

  override protected[distage] def finalizers[F1[_]: TagK]: collection.Seq[PlanInterpreter.Finalizer[F1]] = {
    dependencyMap.finalizers
      .filter(_.fType == SafeType.getK[F1])
      .map(_.asInstanceOf[PlanInterpreter.Finalizer[F1]])
  }

  override def instances: collection.Seq[IdentifiedRef] =
    dependencyMap.enumerate
}
