package com.github.pshirshov.izumi.distage.model.references

import com.github.pshirshov.izumi.distage.model.reflection.universe.{WithDISafeType, DIUniverseBase}

trait WithDIKey {
  this: DIUniverseBase
    with WithDISafeType =>

  sealed trait DIKey {
    def tpe: TypeFull
  }

  object DIKey {

    def get[K: Tag]: TypeKey = TypeKey(SafeType.get[K])

    case class TypeKey(tpe: TypeFull) extends DIKey {
      override def toString: String = tpe.toString

      def named[I: IdContract](id: I): IdKey[I] = IdKey(tpe, id)
    }

    case class IdKey[I: IdContract](tpe: TypeFull, id: I) extends DIKey {
      val idContract: IdContract[I] = implicitly[IdContract[I]]

      override def toString: String = s"${tpe.toString}#$id"
    }

    case class ProxyElementKey(proxied: DIKey, tpe: TypeFull) extends DIKey {
      override def toString: String = s"Proxy[${proxied.toString}]"

      override def hashCode(): Int = toString.hashCode()
    }

    // todo: this disambiguating .index is kinda shitty
    case class SetElementKey(set: DIKey, index: Int, tpe: TypeFull) extends DIKey {
      override def toString: String = s"$set##${tpe.toString}.$index"

      override def hashCode(): Int = toString.hashCode()
    }
  }

  trait IdContract[T] {
    def repr(v: T): String
  }

  implicit def stringIdContract: IdContract[String]
  implicit def singletonStringIdContract[S <: String with Singleton]: IdContract[S]
}
