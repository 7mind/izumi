package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.plan.OrderedPlan
import com.github.pshirshov.izumi.distage.model.provisioning.Provision.ProvisionImmutable
import com.github.pshirshov.izumi.distage.model.references.IdentifiedRef
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

final class LocatorDefaultImpl[F[_]]
(
  val plan: OrderedPlan,
  val parent: Option[Locator],
  val dependencyMap: ProvisionImmutable[F],
) extends AbstractLocator {
  override protected def unsafeLookup(key: RuntimeDIUniverse.DIKey): Option[Any] =
    dependencyMap.get(key)

  override def instances: Seq[IdentifiedRef] =
    dependencyMap.enumerate
}
