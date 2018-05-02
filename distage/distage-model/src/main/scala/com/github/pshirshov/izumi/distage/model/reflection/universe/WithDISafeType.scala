package com.github.pshirshov.izumi.distage.model.reflection.universe

trait WithDISafeType {
  this: DIUniverseBase
  =>

  type TypeFull = SafeType

  // TODO: hotspots, hashcode on keys is inefficient
  case class SafeType(tpe: TypeNative) {
    override lazy val toString: String = tpe.deannotate.toString

    override lazy val hashCode: Int = toString.hashCode

    override def equals(obj: scala.Any): Boolean = obj match {
      case SafeType(other) =>
        tpe.deannotate.dealias =:= other.deannotate.dealias
      case _ =>
        false
    }

    def <:<(that: SafeType): Boolean =
      this.tpe.deannotate.dealias <:< that.tpe.deannotate.dealias
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
