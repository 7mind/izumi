package com.github.pshirshov.izumi.distage.model.reflection

import com.github.pshirshov.izumi.distage.model.functions.Callable
import com.github.pshirshov.izumi.distage.model.plan.Wiring
import com.github.pshirshov.izumi.fundamentals.reflection._

trait ReflectionProvider {
  def symbolToWiring(symbl: TypeFull): Wiring

  def providerToWiring(function:Callable): Wiring
}
