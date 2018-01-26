package com.github.pshirshov.izumi.distage.reflection
import com.github.pshirshov.izumi.distage.TypeFull
import com.github.pshirshov.izumi.distage.model.Callable
import com.github.pshirshov.izumi.distage.model.plan.Wiring

trait ReflectionProvider {
  def symbolToWiring(symbl: TypeFull): Wiring

  def providerToWiring(function:Callable): Wiring
}
