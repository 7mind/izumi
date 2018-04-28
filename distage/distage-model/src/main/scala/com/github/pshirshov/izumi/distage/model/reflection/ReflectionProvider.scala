package com.github.pshirshov.izumi.distage.model.reflection

import com.github.pshirshov.izumi.distage.model.reflection.universe.{DIUniverse, StaticDIUniverse, RuntimeDIUniverse}

trait ReflectionProvider {
  val u: DIUniverse

  def symbolToWiring(symbl: u.TypeFull): u.Wiring

  def providerToWiring(function: u.Provider): u.Wiring

  def constructorParameters(symbl: u.TypeFull): List[u.Association.Parameter]
}

object ReflectionProvider {

  trait Runtime extends ReflectionProvider {
    override val u: RuntimeDIUniverse.type = RuntimeDIUniverse
  }

  trait Static[+M <: StaticDIUniverse[_]] extends ReflectionProvider {
    override val u: M
  }

}
