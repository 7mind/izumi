package com.github.pshirshov.izumi.distage.model.provisioning

import com.github.pshirshov.izumi.distage.model.references.{DIKey, IdentifiedRef}

import scala.collection.Map

trait Provision {
  def instances: Map[DIKey, Any]

  def imports: Map[DIKey, Any]

  def narrow(allRequiredKeys: Set[DIKey]): Provision

  def extend(values: Map[DIKey, Any]): Provision

  final def get(key: DIKey): Option[Any] = instances.get(key).orElse(imports.get(key))

  final def enumerate: Stream[IdentifiedRef] = instances.map(IdentifiedRef.tupled).toStream
}
