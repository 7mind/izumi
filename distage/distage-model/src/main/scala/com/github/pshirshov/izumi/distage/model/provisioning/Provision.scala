package com.github.pshirshov.izumi.distage.model.provisioning

import com.github.pshirshov.izumi.distage.model.references.IdentifiedRef
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

import scala.collection.Map

trait Provision {
  def instances: Map[RuntimeDIUniverse.DIKey, Any]

  def imports: Map[RuntimeDIUniverse.DIKey, Any]

  def narrow(allRequiredKeys: Set[RuntimeDIUniverse.DIKey]): Provision

  def extend(values: Map[RuntimeDIUniverse.DIKey, Any]): Provision

  final def get(key: RuntimeDIUniverse.DIKey): Option[Any] = instances.get(key).orElse(imports.get(key))

  final def enumerate: Stream[IdentifiedRef] = instances.map(IdentifiedRef.tupled).toStream
}
