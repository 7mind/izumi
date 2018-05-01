package com.github.pshirshov.izumi.distage.model.reflection

import com.github.pshirshov.izumi.distage.model.reflection.universe.{DIUniverse, RuntimeDIUniverse, StaticDIUniverse}

trait SymbolIntrospector {
  val u: DIUniverse

  case class SelectedConstructor(constructorSymbol: u.MethodSymb, arguments: List[u.Symb])

  def selectConstructor(symb: u.TypeFull): SelectedConstructor

  def selectParameters(symb: u.MethodSymb): List[u.Symb]

  def isConcrete(symb: u.TypeFull): Boolean

  def isWireableAbstract(symb: u.TypeFull): Boolean

  def isFactory(symb: u.TypeFull): Boolean

  def isWireableMethod(tpe: u.TypeFull, decl: u.Symb): Boolean

  def isFactoryMethod(tpe: u.TypeFull, decl: u.Symb): Boolean
}

object SymbolIntrospector {

  trait Runtime extends SymbolIntrospector {
    override val u: RuntimeDIUniverse.type = RuntimeDIUniverse
  }

  abstract class Static[+M <: StaticDIUniverse[_]](override val u: M) extends SymbolIntrospector

}
