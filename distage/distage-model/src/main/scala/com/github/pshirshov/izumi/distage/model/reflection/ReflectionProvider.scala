package com.github.pshirshov.izumi.distage.model.reflection

import com.github.pshirshov.izumi.distage.model.reflection.universe.{DIUniverse, RuntimeDIUniverse}

trait ReflectionProvider {
  val u: DIUniverse

  def symbolToWiring(symbl: u.SafeType): u.Wiring

  def providerToWiring(function: u.Provider): u.Wiring

  def constructorParameters(symbl: u.SafeType): List[u.Association.Parameter]

  def constructorParameterLists(symbl: u.SafeType): List[List[u.Association.Parameter]]
}

object ReflectionProvider {

  trait Runtime extends ReflectionProvider {
    override val u: RuntimeDIUniverse.type = RuntimeDIUniverse
  }

  type Static[U] = Aux[U]

  type Aux[U] = ReflectionProvider { val u: U }

}
