package org.bitbucket.pshirshov.izumi.di.provisioning

import org.bitbucket.pshirshov.izumi.di.Locator
import org.bitbucket.pshirshov.izumi.di.model.DIKey

trait ProvisioningContext {
  def fetchKey(key: DIKey): Option[Any]

  def importKey(key: DIKey): Option[Any]
}


case class LocatorContext(provision: Provision, locator: Locator) extends ProvisioningContext {
  override def fetchKey(key: DIKey): Option[Any] = provision.get(key)

  override def importKey(key: DIKey): Option[Any] = locator.lookupInstance[Any](key)
}
