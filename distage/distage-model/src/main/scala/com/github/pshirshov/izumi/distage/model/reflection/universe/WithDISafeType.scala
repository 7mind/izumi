package com.github.pshirshov.izumi.distage.model.reflection.universe

trait WithDISafeType {
  this: DIUniverseBase
  =>

  type TypeFull = SafeType

  // TODO: hotspots, hashcode on keys is inefficient
  case class SafeType(tpe: TypeNative) {
    private val dealiased: u.Type = tpe.deannotate.dealias

    override lazy val toString: String = dealiased.toString

    override lazy val hashCode: Int = toString.hashCode

    override def equals(obj: scala.Any): Boolean = obj match {
      case other: SafeType =>
        dealiased =:= other.dealiased
      case _ =>
        false
    }

    def <:<(that: SafeType): Boolean =
      dealiased <:< that.dealiased
  }

  implicit final class Deannotate(typ: TypeNative) {
    def deannotate: TypeNative =
      typ match {
        case t: u.AnnotatedTypeApi =>
          t.underlying
        case _ =>
          typ
      }
  }

  object SafeType {
    def get[T: Tag]: TypeFull = SafeType(u.typeTag[T].tpe)

    def getWeak[T: u.WeakTypeTag]: TypeFull = SafeType(u.weakTypeTag[T].tpe)
  }

}
