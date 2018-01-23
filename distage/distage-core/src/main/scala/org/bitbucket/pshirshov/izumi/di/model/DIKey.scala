package org.bitbucket.pshirshov.izumi.di.model

import org.bitbucket.pshirshov.izumi.di.{Tag, TypeFull, TypeNative}

import scala.reflect.runtime.universe._

sealed trait DIKey {
  def symbol: TypeFull
}

case class EqualitySafeType(tpe: TypeNative) {

  override def toString: String = tpe.toString

  override def hashCode(): Int = tpe.toString.hashCode

  override def equals(obj: scala.Any): Boolean = obj match {
    case EqualitySafeType(otherSymbol) =>
      tpe =:= otherSymbol
    case _ =>
      false
  }
}

object EqualitySafeType {
  def get[T:Tag] = EqualitySafeType(typeTag[T].tpe)
}

object DIKey {
  case class TypeKey(symbol: TypeFull) extends DIKey {
    override def toString: String = symbol.toString

    def named[Id](id: Id): IdKey[Id] = IdKey(symbol, id)
  }

  case class IdKey[InstanceId](symbol: TypeFull, id: InstanceId) extends DIKey {
    override def toString: String = s"${symbol.toString}#$id"
  }

  case class ProxyElementKey(proxied: DIKey, symbol: TypeFull) extends DIKey {
    override def toString: String = s"Proxy[${proxied.toString}]"

    override def hashCode(): Int = toString.hashCode()
  }

  case class SetElementKey(set: DIKey, symbol: TypeFull) extends DIKey {
    override def toString: String = s"Set[${symbol.toString}]#$set"

    override def hashCode(): Int = toString.hashCode()
  }

  def get[K: Tag]: TypeKey = TypeKey(EqualitySafeType.get[K])
}



