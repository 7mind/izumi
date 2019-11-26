package izumi.distage.model.provisioning

import izumi.distage.model.reflection.universe.RuntimeDIUniverse.DIKey

trait ProvisioningKeyProvider {
  /** Return the object referred by `key` if available
    *
    * @param makeByName Wrap the result into a `Function0`,
    *                   if already a by-name proxy, return unchanged.
    *                   If set to `false` and the result is a by-name proxy,
    *                   it will be executed
    */
  def fetchKey(key: DIKey, makeByName: Boolean): Option[Any]

  /** Directly access the value, without de-referencing for by-names **/
  def fetchUnsafe(key: DIKey): Option[Any]

  def importKey(key: DIKey): Option[Any]

  def narrow(allRequiredKeys: Set[DIKey]): ProvisioningKeyProvider

  def extend(values: Map[DIKey, Any]): ProvisioningKeyProvider
}
