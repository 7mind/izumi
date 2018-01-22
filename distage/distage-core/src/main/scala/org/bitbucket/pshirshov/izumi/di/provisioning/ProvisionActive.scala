package org.bitbucket.pshirshov.izumi.di.provisioning

import org.bitbucket.pshirshov.izumi.di.model.DIKey

import scala.collection.mutable

case class ProvisionActive
(
  instances: mutable.HashMap[DIKey, Any] = mutable.HashMap[DIKey, Any]()
  , imports: mutable.HashMap[DIKey, Any] = mutable.HashMap[DIKey, Any]()
) extends Provision {
  def toImmutable: ProvisionImmutable = ProvisionImmutable(instances.toMap, imports.toMap)
}
