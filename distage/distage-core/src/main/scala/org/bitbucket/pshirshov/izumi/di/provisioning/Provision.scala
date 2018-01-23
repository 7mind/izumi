package org.bitbucket.pshirshov.izumi.di.provisioning

import org.bitbucket.pshirshov.izumi.di.IdentifiedRef
import org.bitbucket.pshirshov.izumi.di.model.DIKey

import scala.collection.Map

trait Provision {
  def instances: Map[DIKey, Any]

  def imports: Map[DIKey, Any]

  def narrow(allRequiredKeys: Set[DIKey]): Provision

  final def get(key: DIKey): Option[Any] = instances.get(key).orElse(imports.get(key))

  final def enumerate: Stream[IdentifiedRef] = instances.map(IdentifiedRef.tupled).toStream
}
