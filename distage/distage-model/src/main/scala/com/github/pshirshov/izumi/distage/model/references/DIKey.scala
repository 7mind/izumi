package com.github.pshirshov.izumi.distage.model.references

import com.github.pshirshov.izumi.distage.model.reflection.universe.{DIUniverseBase, RuntimeUniverse, SafeType}

trait DIKey {
  this: DIUniverseBase
    with SafeType =>

  import u._

  sealed trait DIKey {
    def symbol: TypeFull
  }

  object DIKey {

    case class TypeKey(symbol: TypeFull) extends DIKey {
      override def toString: String = symbol.toString

      def named[Id: Liftable](id: Id): IdKey[Id] = IdKey(symbol, id)
    }
    object TypeKey {
      implicit final val liftableTypeKey: Liftable[TypeKey] = {
        case TypeKey(symbol) => q"""
        { new ${symbolOf[RuntimeUniverse.type].asClass.module}.DIKey.TypeKey($symbol) }
          """
      }
    }

    case class IdKey[InstanceId : Liftable](symbol: TypeFull, id: InstanceId) extends DIKey {
      val liftableId: Liftable[InstanceId] = implicitly

      override def toString: String = s"${symbol.toString}#$id"
    }
    object IdKey {
      implicit final def liftableIdKey[InstanceId]: Liftable[IdKey[InstanceId]] = {
        case i@IdKey(symbol, id) => q"""
        { new ${symbolOf[RuntimeUniverse.type].asClass.module}.DIKey.IdKey($symbol, ${i.liftableId(id)}) }
          """
      }
    }

    case class ProxyElementKey(proxied: DIKey, symbol: TypeFull) extends DIKey {
      override def toString: String = s"Proxy[${proxied.toString}]"

      override def hashCode(): Int = toString.hashCode()
    }
    object ProxyElementKey {
      implicit final val liftableProxyElementKey: Liftable[ProxyElementKey] = {
        case ProxyElementKey(proxied, symbol) => q"""
        { new ${symbolOf[RuntimeUniverse.type].asClass.module}.DIKey.ProxyElementKey(${liftableDIKey(proxied)}, $symbol) }
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
        { new ${symbolOf[RuntimeUniverse.type].asClass.module}.DIKey.SetElementKey(${liftableDIKey(set)}, $symbol) }
          """
      }
    }

    def get[K: Tag]: TypeKey = TypeKey(SafeType.get[K])

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
