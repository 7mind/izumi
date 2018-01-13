package org.bitbucket.pshirshov.izumi.di.model

import org.bitbucket.pshirshov.izumi.di.{TypeFull, Tag}
import scala.reflect.runtime.universe._

sealed trait DIKey {
  def symbol: TypeFull
}

object DIKey {
  case class TypeKey(symbol: TypeFull) extends DIKey {
    override def toString: String = symbol.toString

    def named[Id](id: Id): IdKey[Id] = IdKey(symbol, id)

    override def hashCode(): Int = toString.hashCode()

    override def equals(obj: scala.Any): Boolean = obj match {
      case other: TypeKey =>
        symbol =:= other.symbol
      case _ =>
        false
    }
  }

  case class IdKey[InstanceId](symbol: TypeFull, id: InstanceId) extends DIKey {
    override def toString: String = s"${symbol.toString}#$id"

    override def hashCode(): Int = toString.hashCode()

    override def equals(obj: scala.Any): Boolean = obj match {
      case other: IdKey[_] =>
        symbol =:= other.symbol && id == other.id
      case _ =>
        false
    }
  }

  case class SetElementKey[InstanceId](set: DIKey, symbol: TypeFull) extends DIKey {
    override def toString: String = s"Set[${symbol.toString}]#$set"

    override def hashCode(): Int = toString.hashCode()

    override def equals(obj: scala.Any): Boolean = obj match {
      case other: SetElementKey[_] =>
        symbol =:= other.symbol && set == other.set
      case _ =>
        false
    }
  }

  def get[K: Tag]: TypeKey = TypeKey(typeTag[K].tpe)
}



