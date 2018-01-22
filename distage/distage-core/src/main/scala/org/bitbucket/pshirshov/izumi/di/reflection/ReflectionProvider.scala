package org.bitbucket.pshirshov.izumi.di.reflection
import org.bitbucket.pshirshov.izumi.di.TypeFull
import org.bitbucket.pshirshov.izumi.di.model.Callable
import org.bitbucket.pshirshov.izumi.di.model.plan.Wiring

trait ReflectionProvider {
  def symbolToWiring(symbl: TypeFull): Wiring

  def providerToWiring(function:Callable): Wiring
}
