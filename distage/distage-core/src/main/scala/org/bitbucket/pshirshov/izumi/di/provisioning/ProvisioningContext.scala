package org.bitbucket.pshirshov.izumi.di.provisioning

import org.bitbucket.pshirshov.izumi.di.model.DIKey

trait ProvisioningContext {
  def fetchKey(key: DIKey): Option[Any]

  def importKey(key: DIKey): Option[Any]

  def narrow(allRequiredKeys: Set[DIKey]): ProvisioningContext
}



