package org.bitbucket.pshirshov.izumi.di.model

import org.bitbucket.pshirshov.izumi.di.Symb

sealed trait DIKey {
  def symbol: Symb
}

object DIKey {
  case class TypeKey(symbol: Symb) extends DIKey
  case class IdKey[InstanceId](symbol: Symb, id: InstanceId) extends DIKey
}



