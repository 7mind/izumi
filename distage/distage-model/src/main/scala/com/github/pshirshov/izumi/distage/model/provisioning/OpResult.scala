package com.github.pshirshov.izumi.distage.model.provisioning

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

sealed trait OpResult {}

object OpResult {

  final case class DoNothing() extends OpResult

  final case class NewInstance(key: RuntimeDIUniverse.DIKey, value: Any) extends OpResult

  final case class NewImport(key: RuntimeDIUniverse.DIKey, value: Any) extends OpResult

  final case class UpdatedSet(key: RuntimeDIUniverse.DIKey, set: Set[Any]) extends OpResult
}
