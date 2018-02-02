package com.github.pshirshov.izumi.distage.model.reflection

import com.github.pshirshov.izumi.distage.model.functions.Callable
import com.github.pshirshov.izumi.distage.model.plan.Wiring
import com.github.pshirshov.izumi.fundamentals.reflection.{RuntimeUniverse, _}

trait AbstractReflectionProvider {
  type Universe <: DIUniverse
  val u: Universe

  def symbolToWiring(symbl: u.TypeFull): Wiring

  def providerToWiring(function:Callable): Wiring
}

trait ReflectionProvider extends AbstractReflectionProvider {
  override type Universe = RuntimeUniverse.type
  override val u: RuntimeUniverse.type = RuntimeUniverse
}
