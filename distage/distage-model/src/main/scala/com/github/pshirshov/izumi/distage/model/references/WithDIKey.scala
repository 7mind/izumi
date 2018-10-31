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
      override def toString: String = s"{type.${tpe.toString}}"

      def named[I: IdContract](id: I): IdKey[I] = IdKey(tpe, id)
    }

    case class IdKey[I: IdContract](tpe: SafeType, id: I) extends BasicKey {
      val idContract: IdContract[I] = implicitly

      override def toString: String = s"{type.${tpe.toString}.${idContract.repr(id)}}"
    }

    case class ProxyElementKey(proxied: DIKey, tpe: SafeType) extends DIKey {
      override def toString: String = s"{proxy.${proxied.toString}}"

      override def hashCode: Int = toString.hashCode()
    }

    /**
      *
      * @param set Target set key. Must be of type `Set[T]`
      */
    case class SetElementKey(set: DIKey, reference: DIKey) extends DIKey {
      override def tpe: SafeType = reference.tpe

      override def toString: String = {
        s"{set.$set/${reference.toString}}"

      }

      override def hashCode: Int = toString.hashCode()
    }

    implicit class WithTpe(key: DIKey.BasicKey) {
      def withTpe(tpe: SafeType): DIKey.BasicKey = {
        key match {
          case k: TypeKey => k.copy(tpe = tpe)
          case k: IdKey[_] => k.copy(tpe = tpe)(k.idContract)
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
