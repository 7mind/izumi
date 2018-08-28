package com.github.pshirshov.izumi.distage.model.reflection.universe

import com.github.pshirshov.izumi.fundamentals.reflection.WithTags

trait WithDISafeType {
  this: DIUniverseBase with WithTags =>

  // TODO: hotspots, hashcode on keys is inefficient
  case class SafeType(tpe: TypeNative) {
    private val dealiased: u.Type = {
      tpe.dealias.deannotate
    }

    override lazy val toString: String = {
      dealiased.toString
    }

    /**
    * Workaround for Type's hashcode being unstable with sbt fork := false and version of scala other than 2.12.4
    * (two different scala-reflect libraries end up on classpath and lead to undefined hashcode)
    * */
    override val hashCode: Int = {
      dealiased.typeSymbol.name.toString.hashCode
    }

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
    @inline
    def deannotate: TypeNative =
      typ match {
        case t: u.AnnotatedTypeApi =>
          t.underlying
        case _ =>
          typ
      }
  }

  object SafeType {
    def get[T: Tag]: SafeType = SafeType(Tag[T].tag.tpe)

    def unsafeGetWeak[T: WeakTag]: SafeType = SafeType(WeakTag[T].tag.tpe)
  }

  implicit class TagSafeType(tag: Tag[_]) {
    def tpe: SafeType = SafeType(tag.tag.tpe)
  }

}
