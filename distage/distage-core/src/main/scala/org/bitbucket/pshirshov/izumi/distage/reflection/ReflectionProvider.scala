package org.bitbucket.pshirshov.izumi.distage.reflection
import org.bitbucket.pshirshov.izumi.distage.TypeFull
import org.bitbucket.pshirshov.izumi.distage.model.Callable
import org.bitbucket.pshirshov.izumi.distage.model.plan.Wiring

trait ReflectionProvider {
  def symbolToWiring(symbl: TypeFull): Wiring

  def providerToWiring(function:Callable): Wiring
}
