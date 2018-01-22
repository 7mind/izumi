package org.bitbucket.pshirshov.izumi.di.reflection
import org.bitbucket.pshirshov.izumi.di.TypeFull
import org.bitbucket.pshirshov.izumi.di.model.Callable
import org.bitbucket.pshirshov.izumi.di.model.plan.Wireable

trait ReflectionProvider {
  def symbolDeps(symbl: TypeFull): Wireable

  def providerDeps(function:Callable): Wireable

  def isConcrete(symb: TypeFull): Boolean

  def isWireableAbstract(symb: TypeFull): Boolean

  def isFactory(symb: TypeFull): Boolean
}
