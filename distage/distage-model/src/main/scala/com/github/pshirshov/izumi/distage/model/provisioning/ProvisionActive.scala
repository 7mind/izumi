package com.github.pshirshov.izumi.distage.model.provisioning

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

import scala.collection.mutable

final case class ProvisionActive
(
  instances: mutable.LinkedHashMap[RuntimeDIUniverse.DIKey, Any] = mutable.LinkedHashMap[RuntimeDIUniverse.DIKey, Any]()
  , imports: mutable.LinkedHashMap[RuntimeDIUniverse.DIKey, Any] = mutable.LinkedHashMap[RuntimeDIUniverse.DIKey, Any]()
) extends Provision {
  def toImmutable: ProvisionImmutable = {
    ProvisionImmutable(instances, imports)
  }

  override def narrow(allRequiredKeys: Set[RuntimeDIUniverse.DIKey]): Provision = {
    toImmutable.narrow(allRequiredKeys)
  }

  override def extend(values: collection.Map[RuntimeDIUniverse.DIKey, Any]): Provision = {
    toImmutable.extend(values)
  }
}
