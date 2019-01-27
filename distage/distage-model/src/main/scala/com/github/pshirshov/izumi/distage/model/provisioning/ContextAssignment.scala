package com.github.pshirshov.izumi.distage.model.provisioning

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

sealed trait ContextAssignment {}

object ContextAssignment {

  final case class DoNothing() extends ContextAssignment

  final case class NewInstance(key: RuntimeDIUniverse.DIKey, instance: Any) extends ContextAssignment

  final case class NewImport(key: RuntimeDIUniverse.DIKey, instance: Any) extends ContextAssignment

  final case class UpdatedSet(key: RuntimeDIUniverse.DIKey, set: Set[Any]) extends ContextAssignment
}
