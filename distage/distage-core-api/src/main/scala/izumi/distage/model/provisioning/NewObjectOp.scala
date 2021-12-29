package izumi.distage.model.provisioning

import izumi.distage.model.reflection.{DIKey, SafeType}

sealed trait NewObjectOp {
  def key: DIKey
}
object NewObjectOp {
  sealed trait CurrentContextInstance {
    def instance: Any
    def implType: SafeType
  }

  /** Marks a new instance introduced in current locator context */
  final case class NewInstance(key: DIKey, implType: SafeType, instance: Any) extends NewObjectOp with CurrentContextInstance
  /** Marks a new instance introduced in current locator context */
  final case class NewResource[F[_]](key: DIKey, implType: SafeType, instance: Any, finalizer: () => F[Unit]) extends NewObjectOp with CurrentContextInstance

  /** Marks a reused instance from current locator context */
  final case class UseInstance(key: DIKey, instance: Any) extends NewObjectOp
  /** Marks a reused instance from parent locator context */
  final case class NewImport(key: DIKey, instance: Any) extends NewObjectOp

  final case class NewFinalizer[F[_]](key: DIKey, finalizer: () => F[Unit]) extends NewObjectOp
}
