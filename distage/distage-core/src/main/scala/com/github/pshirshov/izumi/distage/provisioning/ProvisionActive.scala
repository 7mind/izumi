package com.github.pshirshov.izumi.distage.provisioning

import com.github.pshirshov.izumi.distage.model.provisioning.{Provision, ProvisionImmutable}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse

import scala.collection.mutable

case class ProvisionActive
(
  instances: mutable.HashMap[RuntimeUniverse.DIKey, Any] = mutable.HashMap[RuntimeUniverse.DIKey, Any]()
  , imports: mutable.HashMap[RuntimeUniverse.DIKey, Any] = mutable.HashMap[RuntimeUniverse.DIKey, Any]()
) extends Provision {
  def toImmutable: ProvisionImmutable = {
    ProvisionImmutable(instances.toMap, imports.toMap)
  }

  override def narrow(allRequiredKeys: Set[RuntimeUniverse.DIKey]): Provision = {
    toImmutable.narrow(allRequiredKeys)
  }

  override def extend(values: collection.Map[RuntimeUniverse.DIKey, Any]): Provision = {
    toImmutable.extend(values)
  }
}
