package org.bitbucket.pshirshov.izumi.di.model

import org.bitbucket.pshirshov.izumi.di.{Tag, TypeFull, TypeFullX}

import scala.reflect.runtime.universe._

sealed trait DIKey {
  def symbol: TypeFull
}

case class EqualitySafeType(symbol: TypeFullX) {
  override def hashCode(): Int = symbol.toString.hashCode

  override def equals(obj: scala.Any): Boolean = obj match {
    case EqualitySafeType(otherSymbol) =>
      symbol =:= otherSymbol
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

  case class SetElementKey[InstanceId](set: DIKey, symbol: TypeFull) extends DIKey {
    override def toString: String = s"Set[${symbol.toString}]#$set"

    override def hashCode(): Int = toString.hashCode()
  }

  def get[K: Tag]: TypeKey = TypeKey(EqualitySafeType.get[K])
}



