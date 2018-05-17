package com.github.pshirshov.izumi.distage.model.references

import com.github.pshirshov.izumi.distage.model.reflection.universe.{WithDISafeType, DIUniverseBase}

trait WithDIKey {
  this: DIUniverseBase
    with WithDISafeType =>

  sealed trait DIKey {
    def symbol: TypeFull
  }

  object DIKey {

    def get[K: Tag]: TypeKey = TypeKey(SafeType.get[K])

    case class TypeKey(symbol: TypeFull) extends DIKey {
      override def toString: String = symbol.toString

      def named[I: IdContract](id: I): IdKey[I] = IdKey(symbol, id)
    }

    case class IdKey[I: IdContract](symbol: TypeFull, id: I) extends DIKey {
      val idContract: IdContract[I] = implicitly[IdContract[I]]

      override def toString: String = s"${symbol.toString}#$id"
    }

    case class ProxyElementKey(proxied: DIKey, symbol: TypeFull) extends DIKey {
      override def toString: String = s"Proxy[${proxied.toString}]"

      override def hashCode(): Int = toString.hashCode()
    }

    case class SetElementKey(set: DIKey, symbol: TypeFull) extends DIKey {
      override def toString: String = s"$set##${symbol.toString}"

      override def hashCode(): Int = toString.hashCode()
    }
  }

  trait IdContract[T] {
    def repr(v: T): String
  }

  implicit def stringIdContract: IdContract[String]
  implicit def singletonStringIdContract[S <: String with Singleton]: IdContract[S]
}
