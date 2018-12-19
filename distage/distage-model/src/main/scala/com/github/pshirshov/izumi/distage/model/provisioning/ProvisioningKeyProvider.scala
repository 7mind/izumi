package com.github.pshirshov.izumi.distage.model.provisioning

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

trait ProvisioningKeyProvider {
  /** Return the referred object if available, dereferencing by-name proxies according to the argument **/
  def fetchKey(key: RuntimeDIUniverse.DIKey, byName: Boolean): Option[Any]

  /** Directly access the value, without dereferencing for by-names **/
  def fetchUnsafe(key: RuntimeDIUniverse.DIKey): Option[Any]

  def importKey(key: RuntimeDIUniverse.DIKey): Option[Any]

  def narrow(allRequiredKeys: Set[RuntimeDIUniverse.DIKey]): ProvisioningKeyProvider

  def extend(values: Map[RuntimeDIUniverse.DIKey, Any]): ProvisioningKeyProvider
}



