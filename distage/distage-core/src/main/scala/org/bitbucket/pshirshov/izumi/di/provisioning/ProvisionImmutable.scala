package org.bitbucket.pshirshov.izumi.di.provisioning

import org.bitbucket.pshirshov.izumi.di.model.DIKey

import scala.collection.Map

case class ProvisionImmutable
(
  instances: Map[DIKey, Any]
  , imports: Map[DIKey, Any]
) extends Provision {
  override def narrow(allRequiredKeys: Set[DIKey]): Provision = {
    ProvisionImmutable(
      instances.filterKeys(allRequiredKeys.contains)
      , imports.filterKeys(allRequiredKeys.contains)
    )
  }
}
