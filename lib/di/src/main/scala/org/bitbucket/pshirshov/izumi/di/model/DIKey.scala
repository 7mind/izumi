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
  }

  case class IdKey[InstanceId](symbol: TypeFull, id: InstanceId) extends DIKey {
    override def toString: String = s"${symbol.toString}#$id"
  }

  case class SetElementKey[InstanceId](set: DIKey, symbol: TypeFull) extends DIKey {
    override def toString: String = s"Set[${symbol.toString}]#$set"
  }

  def get[K: Tag]: TypeKey = TypeKey(typeTag[K].tpe)
}



