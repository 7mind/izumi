package com.github.pshirshov.izumi.distage.model.provisioning

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

import scala.collection.Map

final case class ProvisionImmutable
(
  instances: Map[RuntimeDIUniverse.DIKey, Any]
  , imports: Map[RuntimeDIUniverse.DIKey, Any]
) extends Provision {
  override def narrow(allRequiredKeys: Set[RuntimeDIUniverse.DIKey]): Provision = {
    ProvisionImmutable(
      instances.filterKeys(allRequiredKeys.contains).toMap // 2.13 compat
      , imports.filterKeys(allRequiredKeys.contains).toMap // 2.13 compat
    )
  }

  override def extend(values: Map[RuntimeDIUniverse.DIKey, Any]): Provision = {
    ProvisionImmutable(
      instances ++ values
      , imports
    )
  }
}
