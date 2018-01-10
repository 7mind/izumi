package org.bitbucket.pshirshov.izumi.di.definition

import org.bitbucket.pshirshov.izumi.di.model.DIKey

import scala.reflect.runtime.universe._

sealed trait Def {
  def target: DIKey
  def implementation: Symbol
}

object Def {
  case class SingletonBinding(target: DIKey, implementation: Symbol) extends Def
}
