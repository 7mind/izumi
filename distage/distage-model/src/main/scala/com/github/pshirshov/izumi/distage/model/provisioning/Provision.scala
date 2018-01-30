package com.github.pshirshov.izumi.distage.model.provisioning

import com.github.pshirshov.izumi.distage.model.references.IdentifiedRef
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse

import scala.collection.Map

trait Provision {
  def instances: Map[RuntimeUniverse.DIKey, Any]

  def imports: Map[RuntimeUniverse.DIKey, Any]

  def narrow(allRequiredKeys: Set[RuntimeUniverse.DIKey]): Provision

  def extend(values: Map[RuntimeUniverse.DIKey, Any]): Provision

  final def get(key: RuntimeUniverse.DIKey): Option[Any] = instances.get(key).orElse(imports.get(key))

  final def enumerate: Stream[IdentifiedRef] = instances.map(IdentifiedRef.tupled).toStream
}
