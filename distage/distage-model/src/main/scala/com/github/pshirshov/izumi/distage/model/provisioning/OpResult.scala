package com.github.pshirshov.izumi.distage.model.provisioning

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

sealed trait OpResult {}

object OpResult {

  case class DoNothing() extends OpResult

  case class NewInstance(key: RuntimeDIUniverse.DIKey, value: Any) extends OpResult

  case class NewImport(key: RuntimeDIUniverse.DIKey, value: Any) extends OpResult

  case class UpdatedSet(key: RuntimeDIUniverse.DIKey, set: Set[Any]) extends OpResult
}
