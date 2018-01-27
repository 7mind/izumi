package com.github.pshirshov.izumi.distage.reflection
import com.github.pshirshov.izumi.distage.model.functions.Callable
import com.github.pshirshov.izumi.fundamentals.reflection._
import com.github.pshirshov.izumi.distage.model.plan.Wiring

trait ReflectionProvider {
  def symbolToWiring(symbl: TypeFull): Wiring

  def providerToWiring(function:Callable): Wiring
}
