package org.bitbucket.pshirshov.izumi.di.reflection
import org.bitbucket.pshirshov.izumi.di.TypeFull
import org.bitbucket.pshirshov.izumi.di.model.Callable
import org.bitbucket.pshirshov.izumi.di.model.plan.Wiring

trait ReflectionProvider {
  def symbolDeps(symbl: TypeFull): Wiring

  def providerDeps(function:Callable): Wiring

  def isConcrete(symb: TypeFull): Boolean

  def isWireableAbstract(symb: TypeFull): Boolean

  def isFactory(symb: TypeFull): Boolean
}
