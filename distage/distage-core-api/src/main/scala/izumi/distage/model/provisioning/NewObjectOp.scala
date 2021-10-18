package izumi.distage.model.provisioning

import izumi.distage.model.reflection.{DIKey, SafeType}

sealed trait NewObjectOp {
  def key: DIKey
}
object NewObjectOp {
  sealed trait LocalInstance {
    def instance: Any
    def implKey: SafeType
  }

  /** Marks a new instance introduced in current locator context
    */
  final case class NewInstance(key: DIKey, implKey: SafeType, instance: Any) extends NewObjectOp with LocalInstance
  final case class NewResource[F[_]](key: DIKey, implKey: SafeType, instance: Any, finalizer: () => F[Unit]) extends NewObjectOp with LocalInstance

  /** Marks a reused instance from current locator context
    */
  final case class UseInstance(key: DIKey, instance: Any) extends NewObjectOp
  final case class NewFinalizer[F[_]](key: DIKey, finalizer: () => F[Unit]) extends NewObjectOp
  final case class NewImport(key: DIKey, instance: Any) extends NewObjectOp
  final case class UpdatedSet(key: DIKey, set: Set[Any]) extends NewObjectOp
}
