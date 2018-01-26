package com.github.pshirshov.izumi.distage.provisioning

import com.github.pshirshov.izumi.distage.Locator
import com.github.pshirshov.izumi.distage.model.DIKey

case class LocatorContext(provision: Provision, locator: Locator) extends ProvisioningContext {
  override def fetchKey(key: DIKey): Option[Any] = provision.get(key)

  override def importKey(key: DIKey): Option[Any] = locator.lookupInstance[Any](key)

  override def narrow(allRequiredKeys: Set[DIKey]): ProvisioningContext = {
    LocatorContext(provision.narrow(allRequiredKeys), locator)
  }

  override def extend(values: Map[DIKey, Any]): ProvisioningContext = {
    LocatorContext(provision.extend(values: Map[DIKey, Any]), locator)
  }
}
