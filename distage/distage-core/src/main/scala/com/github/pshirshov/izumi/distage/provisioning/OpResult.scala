package com.github.pshirshov.izumi.distage.provisioning

import com.github.pshirshov.izumi.distage.model.DIKey

sealed trait OpResult {}

object OpResult {

  case class NewInstance(key: DIKey, value: Any) extends OpResult

  case class NewImport(key: DIKey, value: Any) extends OpResult

  case class UpdatedSet(key: DIKey, set: Set[Any]) extends OpResult
}