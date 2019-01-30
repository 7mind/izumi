package com.github.pshirshov.izumi.distage.model.provisioning

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

sealed trait ExecutableOpResult

object ExecutableOpResult {

  final case class DoNothing() extends ExecutableOpResult

  final case class NewInstance(key: RuntimeDIUniverse.DIKey, instance: Any) extends ExecutableOpResult

  final case class NewImport(key: RuntimeDIUniverse.DIKey, instance: Any) extends ExecutableOpResult

  final case class UpdatedSet(key: RuntimeDIUniverse.DIKey, set: Set[Any]) extends ExecutableOpResult
}
