package com.github.pshirshov.izumi.distage.provisioning

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.provisioning.{Provision, ProvisioningKeyProvider}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

final case class LocatorContext(provision: Provision, locator: Locator) extends ProvisioningKeyProvider {
  override def fetchKey(key: RuntimeDIUniverse.DIKey): Option[Any] = provision.get(key)

  override def importKey(key: RuntimeDIUniverse.DIKey): Option[Any] = locator.lookupInstance[Any](key)

  override def narrow(allRequiredKeys: Set[RuntimeDIUniverse.DIKey]): ProvisioningKeyProvider = {
    LocatorContext(provision.narrow(allRequiredKeys), locator)
  }

  override def extend(values: Map[RuntimeDIUniverse.DIKey, Any]): ProvisioningKeyProvider = {
    LocatorContext(provision.extend(values: Map[RuntimeDIUniverse.DIKey, Any]), locator)
  }
}
