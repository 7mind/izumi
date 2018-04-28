package com.github.pshirshov.izumi.distage.model.reflection

import com.github.pshirshov.izumi.distage.model.reflection.universe.{DIUniverse, StaticDIUniverse, RuntimeDIUniverse}

trait DependencyKeyProvider {
  val u: DIUniverse

  def keyFromParameter(context: u.DependencyContext.ParameterContext, parameterSymbol: u.Symb): u.DIKey

  def keyFromMethod(context: u.DependencyContext.MethodContext, methodSymbol: u.MethodSymb): u.DIKey
}

object DependencyKeyProvider {

  trait Runtime extends DependencyKeyProvider {
    val u: RuntimeDIUniverse.type = RuntimeDIUniverse
  }

  abstract class Static[+M <: StaticDIUniverse[_]](override val u: M) extends DependencyKeyProvider
}
