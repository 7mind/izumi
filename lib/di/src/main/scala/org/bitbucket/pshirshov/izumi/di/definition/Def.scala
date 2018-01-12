package org.bitbucket.pshirshov.izumi.di.definition

import org.bitbucket.pshirshov.izumi.di.model.DIKey

sealed trait Def {
  def target: DIKey
}

object Def {
  case class SingletonBinding(target: DIKey, implementation: ImplDef) extends Def
  case class SetBinding(target: DIKey, implementation: ImplDef) extends Def
  case class EmptySetBinding(target: DIKey) extends Def
}
