package com.github.pshirshov.izumi.distage.model.provisioning

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse

import scala.collection.Map

case class ProvisionImmutable
(
  instances: Map[RuntimeUniverse.DIKey, Any]
  , imports: Map[RuntimeUniverse.DIKey, Any]
) extends Provision {
  override def narrow(allRequiredKeys: Set[RuntimeUniverse.DIKey]): Provision = {
    ProvisionImmutable(
      instances.filterKeys(allRequiredKeys.contains)
      , imports.filterKeys(allRequiredKeys.contains)
    )
  }

  override def extend(values: Map[RuntimeUniverse.DIKey, Any]): Provision = {
    ProvisionImmutable(
      instances ++ values
      , imports
    )
  }
}
