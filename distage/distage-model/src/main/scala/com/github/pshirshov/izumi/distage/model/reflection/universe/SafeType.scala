package com.github.pshirshov.izumi.distage.model.reflection.universe

trait SafeType {
  this: DIUniverseBase =>

  type TypeFull = SafeType

  case class SafeType(tpe: TypeNative) {
    override def toString: String = tpe.toString

    override def hashCode(): Int = tpe.toString.hashCode

    override def equals(obj: scala.Any): Boolean = obj match {
      case SafeType(other) =>
        tpe =:= other
      case _ =>
        false
    }
  }

  object SafeType {
    def get[T: Tag]: TypeFull = SafeType(u.typeTag[T].tpe)

    def getWeak[T: u.WeakTypeTag]: TypeFull = SafeType(u.weakTypeTag[T].tpe)

    implicit final val liftableSafeType: u.Liftable[SafeType] =
      value => {
        import u._
        q"{ ${symbolOf[RuntimeUniverse.type].asClass.module}.SafeType.getWeak[${Liftable.liftType(value.tpe)}] }"
      }
  }

}
