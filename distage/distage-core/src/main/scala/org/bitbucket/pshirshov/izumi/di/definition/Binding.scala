package org.bitbucket.pshirshov.izumi.di.definition

import org.bitbucket.pshirshov.izumi.di.model.DIKey

sealed trait Binding {
  def target: DIKey
}

object Binding {
  case class SingletonBinding(target: DIKey, implementation: ImplDef) extends Binding
  case class SetBinding(target: DIKey, implementation: ImplDef) extends Binding
  case class EmptySetBinding(target: DIKey) extends Binding
}

