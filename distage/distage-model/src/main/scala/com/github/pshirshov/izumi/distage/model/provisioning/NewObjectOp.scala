package com.github.pshirshov.izumi.distage.model.provisioning

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

sealed trait NewObjectOp

object NewObjectOp {

  final case class DoNothing() extends NewObjectOp

  final case class NewInstance(key: RuntimeDIUniverse.DIKey, instance: Any) extends NewObjectOp

  final case class NewResource[F[_]](key: RuntimeDIUniverse.DIKey, instance: Any, finalizer: () => F[Unit]) extends NewObjectOp

  final case class NewImport(key: RuntimeDIUniverse.DIKey, instance: Any) extends NewObjectOp

  final case class UpdatedSet(key: RuntimeDIUniverse.DIKey, set: Set[Any]) extends NewObjectOp
}
