package com.github.pshirshov.izumi.distage.model.reflection

import com.github.pshirshov.izumi.distage.model.reflection.universe.{DIUniverse, MacroUniverse, RuntimeUniverse}

trait ReflectionProvider {
  val u: DIUniverse

  def symbolToWiring(symbl: u.TypeFull): u.Wiring

  def providerToWiring(function: u.Callable): u.Wiring

  def constructorParameters(symbl: u.TypeFull): List[u.Association.Parameter]
}

object ReflectionProvider {

  trait Java extends ReflectionProvider {
    override val u: RuntimeUniverse.type = RuntimeUniverse
  }

  trait Macro[+M <: MacroUniverse[_]] extends ReflectionProvider {
    override val u: M
  }

}
