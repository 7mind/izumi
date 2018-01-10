package org.bitbucket.pshirshov.izumi.di.definition

import org.bitbucket.pshirshov.izumi.di.model.DIKey

sealed trait Def {
  def target: DIKey
  def implementation: ImplDef
}

object Def {
  case class SingletonBinding(target: DIKey, implementation: ImplDef) extends Def
}
