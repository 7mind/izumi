package izumi.distage.model.provisioning

import izumi.distage.model.reflection.universe.RuntimeDIUniverse.DIKey

sealed trait NewObjectOp {
  type F0[_]
}
object NewObjectOp {
  final case class NewInstance(key: DIKey, instance: Any) extends NewObjectOp
  final case class NewResource[F[_]](key: DIKey, instance: Any, finalizer: () => F[Unit]) extends NewObjectOp {
    override type F0[A] = F[A]
  }
  final case class NewFinalizer[F[_]](key: DIKey, finalizer: () => F[Unit]) extends NewObjectOp {
    override type F0[A] = F[A]
  }
  final case class NewImport(key: DIKey, instance: Any) extends NewObjectOp
  final case class UpdatedSet(key: DIKey, set: Set[Any]) extends NewObjectOp
}
