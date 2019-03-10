package com.github.pshirshov.izumi.distage.model.provisioning

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.DIKey

trait ProvisioningKeyProvider {
  /** Return the object referred by `key` if available
    *
    * @param byName if true and the value under `key` is a by-name `Function0` proxy,
    *               `Function0` will be executed and the result will be returned,
    *               otherwise the by-name `Function0` itself will be returned.
    */
  def fetchKey(key: DIKey, byName: Boolean): Option[Any]

  /** Directly access the value, without de-referencing for by-names **/
  def fetchUnsafe(key: DIKey): Option[Any]

  def importKey(key: DIKey): Option[Any]

  def narrow(allRequiredKeys: Set[DIKey]): ProvisioningKeyProvider

  def extend(values: Map[DIKey, Any]): ProvisioningKeyProvider
}



