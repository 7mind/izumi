package com.github.pshirshov.izumi.distage.model.references

import com.github.pshirshov.izumi.fundamentals.reflection.{EqualitySafeType, RuntimeUniverse}

sealed trait DIKey {
  def symbol: RuntimeUniverse.TypeFull
}


object DIKey {

  case class TypeKey(symbol: RuntimeUniverse.TypeFull) extends DIKey {
    override def toString: String = symbol.toString

    def named[Id](id: Id): IdKey[Id] = IdKey(symbol, id)
  }

  case class IdKey[InstanceId](symbol: RuntimeUniverse.TypeFull, id: InstanceId) extends DIKey {
    override def toString: String = s"${symbol.toString}#$id"
  }

  case class ProxyElementKey(proxied: DIKey, symbol: RuntimeUniverse.TypeFull) extends DIKey {
    override def toString: String = s"Proxy[${proxied.toString}]"

    override def hashCode(): Int = toString.hashCode()
  }

  case class SetElementKey(set: DIKey, symbol: RuntimeUniverse.TypeFull) extends DIKey {
    override def toString: String = s"Set[${symbol.toString}]#$set"

    override def hashCode(): Int = toString.hashCode()
  }

  def get[K: RuntimeUniverse.Tag]: TypeKey = TypeKey(EqualitySafeType.get[K])
}



