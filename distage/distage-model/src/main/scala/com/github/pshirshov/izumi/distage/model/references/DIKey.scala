package com.github.pshirshov.izumi.distage.model.references

import com.github.pshirshov.izumi.distage.model.reflection.universe.{DILiftableRuntimeUniverse, DISafeType, DIUniverseBase, RuntimeDIUniverse}

trait DIKey {
  this: DIUniverseBase
    with DISafeType
    with DILiftableRuntimeUniverse =>

  import u._

  sealed trait DIKey {
    def symbol: TypeFull
  }

  object DIKey {

    def get[K: Tag]: TypeKey = TypeKey(SafeType.get[K])

    case class TypeKey(symbol: TypeFull) extends DIKey {
      override def toString: String = symbol.toString

      def named[Id: Liftable](id: Id): IdKey[Id] = IdKey(symbol, id)
    }
    object TypeKey {
      implicit final val liftableTypeKey: Liftable[TypeKey] = {
        case TypeKey(symbol) => q"""
        { new $RuntimeDIUniverse.DIKey.TypeKey($symbol) }
          """
      }
    }

    case class IdKey[InstanceId](symbol: TypeFull, id: InstanceId)(implicit val liftableId: Liftable[InstanceId]) extends DIKey {
      override def toString: String = s"${symbol.toString}#$id"
    }
    object IdKey {
      implicit final def liftableIdKey[InstanceId]: Liftable[IdKey[InstanceId]] = {
        case idKey: IdKey[_] =>
          import idKey._
          q"""{ new $RuntimeDIUniverse.DIKey.IdKey($symbol, $id) }"""
      }
    }

    case class ProxyElementKey(proxied: DIKey, symbol: TypeFull) extends DIKey {
      override def toString: String = s"Proxy[${proxied.toString}]"

      override def hashCode(): Int = toString.hashCode()
    }
    object ProxyElementKey {
      implicit final val liftableProxyElementKey: Liftable[ProxyElementKey] = {
        case ProxyElementKey(proxied, symbol) => q"""
        { new $RuntimeDIUniverse.DIKey.ProxyElementKey(${liftableDIKey(proxied)}, $symbol) }
          """
      }
    }

    case class SetElementKey(set: DIKey, symbol: TypeFull) extends DIKey {
      override def toString: String = s"Set[${symbol.toString}]#$set"

      override def hashCode(): Int = toString.hashCode()
    }
    object SetElementKey {
      implicit final val liftableSetElementKey: Liftable[SetElementKey] = {
        case SetElementKey(set, symbol) => q"""
        { new $RuntimeDIUniverse.DIKey.SetElementKey(${liftableDIKey(set)}, $symbol) }
          """
      }
    }

    implicit final val liftableDIKey: Liftable[DIKey] = {
      Liftable[DIKey] {
        case t: TypeKey => q"$t"
        case i: IdKey[_] => q"$i"
        case p: ProxyElementKey => q"$p"
        case s: SetElementKey => q"$s"
      }
    }
  }

}
