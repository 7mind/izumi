package com.github.pshirshov.izumi.distage.model.provisioning

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

trait ProvisioningKeyProvider {
  def fetchKey(key: RuntimeDIUniverse.DIKey, byName: Boolean): Option[Any]

  def importKey(key: RuntimeDIUniverse.DIKey): Option[Any]

  def narrow(allRequiredKeys: Set[RuntimeDIUniverse.DIKey]): ProvisioningKeyProvider

  def extend(values: Map[RuntimeDIUniverse.DIKey, Any]): ProvisioningKeyProvider
}



