package com.github.pshirshov.izumi.distage.model.provisioning

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse

sealed trait OpResult {}

object OpResult {

  case class DoNothing() extends OpResult

  case class NewInstance(key: RuntimeUniverse.DIKey, value: Any) extends OpResult

  case class NewImport(key: RuntimeUniverse.DIKey, value: Any) extends OpResult

  case class UpdatedSet(key: RuntimeUniverse.DIKey, set: Set[Any]) extends OpResult
}
