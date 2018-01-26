package com.github.pshirshov.izumi.distage.provisioning

import com.github.pshirshov.izumi.distage.model.DIKey

trait ProvisioningContext {
  def fetchKey(key: DIKey): Option[Any]

  def importKey(key: DIKey): Option[Any]

  def narrow(allRequiredKeys: Set[DIKey]): ProvisioningContext

  def extend(values: Map[DIKey, Any]): ProvisioningContext
}



