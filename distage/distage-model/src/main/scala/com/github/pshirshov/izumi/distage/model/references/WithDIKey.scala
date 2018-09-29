package com.github.pshirshov.izumi.distage.model.references

import com.github.pshirshov.izumi.distage.model.reflection.universe.{DIUniverseBase, WithDISafeType}
import com.github.pshirshov.izumi.fundamentals.reflection.WithTags

trait WithDIKey {
  this: DIUniverseBase
    with WithDISafeType
    with WithTags =>

  sealed trait DIKey {
    def tpe: SafeType
  }

  object DIKey {

    def get[K: Tag]: TypeKey = TypeKey(SafeType.get[K])

    sealed trait BasicKey extends DIKey

    case class TypeKey(tpe: SafeType) extends BasicKey {
      override def toString: String = tpe.toString

      def named[I: IdContract](id: I): IdKey[I] = IdKey(tpe, id)
    }

    case class IdKey[I: IdContract](tpe: SafeType, id: I) extends BasicKey {
      val idContract: IdContract[I] = implicitly

      override def toString: String = s"${tpe.toString}#${idContract.repr(id)}"
    }

    case class ProxyElementKey(proxied: DIKey, tpe: SafeType) extends DIKey {
      override def toString: String = s"Proxy[${proxied.toString}]"

      override def hashCode: Int = toString.hashCode()
    }

    /**
      *
      * @param set Target set key. Must be of type `Set[T]`
      * @param tpe Element type. Must be of type `T1 &lt: T`
      * @param index Disambiguation value. Set elements are of the same type, index makes them different
      * @todo Disambiguating by [[index]] is kinda shitty
      */
    case class SetElementKey(set: DIKey, tpe: SafeType, index: Int) extends DIKey {
      override def toString: String = s"$set##${tpe.toString}.$index"

      override def hashCode: Int = toString.hashCode()
    }

    implicit class WithTpe(key: DIKey) {
      def withTpe(tpe: SafeType) = {
        key match {
          case k: TypeKey => k.copy(tpe = tpe)
          case k: IdKey[_] => k.copy(tpe = tpe)(k.idContract)
          case k: ProxyElementKey => k.copy(tpe = tpe)
          case k: SetElementKey => k.copy(tpe = tpe)
        }
      }
    }
  }

  trait IdContract[T] {
    def repr(v: T): String
  }

  implicit def stringIdContract: IdContract[String]
  implicit def singletonStringIdContract[S <: String with Singleton]: IdContract[S]
}
