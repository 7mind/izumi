package com.github.pshirshov.izumi.distage.model.reflection

import com.github.pshirshov.izumi.distage.model.reflection.universe.{DIUniverse, MacroUniverse, RuntimeUniverse}

trait DependencyKeyProvider {
  val u: DIUniverse

  def keyFromParameter(context: u.DependencyContext.ParameterContext, parameterSymbol: u.Symb): u.DIKey

  def keyFromMethod(context: u.DependencyContext.MethodContext, methodSymbol: u.MethodSymb): u.DIKey
}

object DependencyKeyProvider {

  trait Java extends DependencyKeyProvider {
    val u: RuntimeUniverse.type = RuntimeUniverse
  }

  abstract class Macro[+M <: MacroUniverse[_]](override val u: M) extends DependencyKeyProvider
}
