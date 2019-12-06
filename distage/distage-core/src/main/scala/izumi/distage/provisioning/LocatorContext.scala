package izumi.distage.provisioning

import izumi.distage.model.Locator
import izumi.distage.model.provisioning.proxies.ProxyDispatcher.ByNameWrapper
import izumi.distage.model.provisioning.{Provision, ProvisioningKeyProvider}
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.DIKey

final case class LocatorContext
(
  provision: Provision[Any],
  locator: Locator,
) extends ProvisioningKeyProvider {

  override def fetchUnsafe(key: DIKey): Option[Any] = {
    provision.get(key)
  }

  override def fetchKey(key: DIKey, byName: Boolean): Option[Any] = {
    fetchUnsafe(key).map {
      case dep: ByNameWrapper if !byName =>
        dep.apply()
      case dep if !byName =>
        dep
      case dep: ByNameWrapper if byName =>
        dep
      case dep if byName =>
        () => dep
    }
  }

  override def importKey(key: DIKey): Option[Any] = {
    locator.lookupInstance[Any](key)
  }

  override def narrow(allRequiredKeys: Set[DIKey]): ProvisioningKeyProvider = {
    LocatorContext(provision.narrow(allRequiredKeys), locator)
  }

  override def extend(values: Map[DIKey, Any]): ProvisioningKeyProvider = {
    LocatorContext(provision.extend(values: Map[DIKey, Any]), locator)
  }
}
