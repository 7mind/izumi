package org.bitbucket.pshirshov.izumi.di.provisioning

import org.bitbucket.pshirshov.izumi.di.IdentifiedRef
import org.bitbucket.pshirshov.izumi.di.model.DIKey

import scala.collection.{Map, mutable}

trait Provision {
  def instances: Map[DIKey, Any]

  def imports: Map[DIKey, Any]

  final def get(key: DIKey): Option[Any] = instances.get(key).orElse(imports.get(key))

  final def enumerate: Stream[IdentifiedRef] = instances.map(IdentifiedRef.tupled).toStream
}

case class ActiveProvision
(
  instances: mutable.HashMap[DIKey, Any] = mutable.HashMap[DIKey, Any]()
  , imports: mutable.HashMap[DIKey, Any] = mutable.HashMap[DIKey, Any]()
) extends Provision