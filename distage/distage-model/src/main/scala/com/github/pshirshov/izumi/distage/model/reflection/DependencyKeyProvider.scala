package com.github.pshirshov.izumi.distage.model.reflection

import com.github.pshirshov.izumi.distage.model.reflection.universe.{DIUniverse, RuntimeDIUniverse}

trait DependencyKeyProvider {
  val u: DIUniverse

  def keyFromParameter(context: u.DependencyContext.ParameterContext, parameterSymbol: u.SymbolInfo): u.DIKey

  def keyFromMethod(context: u.DependencyContext.MethodContext, methodSymbol: u.SymbolInfo): u.DIKey

  def resultOfFactoryMethod(context: u.DependencyContext.MethodParameterContext): u.TypeFull

  def keyFromParameterType(parameterType: u.TypeFull): u.DIKey
}

object DependencyKeyProvider {

  trait Runtime extends DependencyKeyProvider {
    override val u: RuntimeDIUniverse.type = RuntimeDIUniverse
  }

  type Static[U] = Aux[U]

  type Aux[U] = DependencyKeyProvider { val u: U }

}
