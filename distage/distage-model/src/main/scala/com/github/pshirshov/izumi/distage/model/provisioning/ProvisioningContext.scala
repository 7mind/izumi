package com.github.pshirshov.izumi.distage.model.provisioning

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

trait ProvisioningContext {
  def fetchKey(key: RuntimeDIUniverse.DIKey): Option[Any]

  def importKey(key: RuntimeDIUniverse.DIKey): Option[Any]

  def narrow(allRequiredKeys: Set[RuntimeDIUniverse.DIKey]): ProvisioningContext

  def extend(values: Map[RuntimeDIUniverse.DIKey, Any]): ProvisioningContext
}



