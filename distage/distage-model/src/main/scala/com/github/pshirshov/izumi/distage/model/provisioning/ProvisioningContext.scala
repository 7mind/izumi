package com.github.pshirshov.izumi.distage.model.provisioning

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse

trait ProvisioningContext {
  def fetchKey(key: RuntimeUniverse.DIKey): Option[Any]

  def importKey(key: RuntimeUniverse.DIKey): Option[Any]

  def narrow(allRequiredKeys: Set[RuntimeUniverse.DIKey]): ProvisioningContext

  def extend(values: Map[RuntimeUniverse.DIKey, Any]): ProvisioningContext
}



