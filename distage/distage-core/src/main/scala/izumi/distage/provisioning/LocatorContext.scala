package izumi.distage.provisioning

import izumi.distage.model.Locator
import izumi.distage.model.provisioning.strategies.ProxyDispatcher.ByNameDispatcher
import izumi.distage.model.provisioning.{Provision, ProvisioningKeyProvider}
import izumi.distage.model.reflection.universe
import izumi.distage.model.reflection.universe.RuntimeDIUniverse

final case class LocatorContext(
                                 provision: Provision[Any]
                               , locator: Locator
                               ) extends ProvisioningKeyProvider {

  override def fetchUnsafe(key: universe.RuntimeDIUniverse.DIKey): Option[Any] = {
    provision.get(key)
  }

  override def fetchKey(key: RuntimeDIUniverse.DIKey, byName: Boolean): Option[Any] = {
    fetchUnsafe(key).map {
      case dep: ByNameDispatcher if !byName =>
        dep.apply()
      case dep if !byName =>
        dep
      case dep: ByNameDispatcher if byName =>
        dep
      case dep if byName =>
        () => dep
    }
  }

  override def importKey(key: RuntimeDIUniverse.DIKey): Option[Any] = {
    locator.lookupInstance[Any](key)
  }

  override def narrow(allRequiredKeys: Set[RuntimeDIUniverse.DIKey]): ProvisioningKeyProvider = {
    LocatorContext(provision.narrow(allRequiredKeys), locator)
  }

  override def extend(values: Map[RuntimeDIUniverse.DIKey, Any]): ProvisioningKeyProvider = {
    LocatorContext(provision.extend(values: Map[RuntimeDIUniverse.DIKey, Any]), locator)
  }
}
