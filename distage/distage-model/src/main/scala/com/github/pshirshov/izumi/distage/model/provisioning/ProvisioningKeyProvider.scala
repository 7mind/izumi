package com.github.pshirshov.izumi.distage.model.provisioning

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

trait ProvisioningKeyProvider {
  /** This method returns the context value taking care of by-name form
    *
    */
  def fetchKey(key: RuntimeDIUniverse.DIKey, byName: Boolean): Option[Any]

  /** Direct access to the value, without by-name specific transformations
    *
    */
  def fetchUnsafe(key: RuntimeDIUniverse.DIKey): Option[Any]

  def importKey(key: RuntimeDIUniverse.DIKey): Option[Any]

  def narrow(allRequiredKeys: Set[RuntimeDIUniverse.DIKey]): ProvisioningKeyProvider

  def extend(values: Map[RuntimeDIUniverse.DIKey, Any]): ProvisioningKeyProvider
}



