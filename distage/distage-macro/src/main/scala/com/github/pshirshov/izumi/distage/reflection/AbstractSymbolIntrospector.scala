package com.github.pshirshov.izumi.distage.reflection

import com.github.pshirshov.izumi.fundamentals.reflection.DIUniverse

trait AbstractSymbolIntrospector {
  val u: DIUniverse

  def selectConstructor(symb: u.TypeFull): u.SelectedConstructor

  def selectParameters(symb: u.MethodSymb): List[u.TypeSymb]

  def isConcrete(symb: u.TypeFull): Boolean

  def isWireableAbstract(symb: u.TypeFull): Boolean

  def isFactory(symb: u.TypeFull): Boolean

  def isWireableMethod(tpe: u.TypeFull, decl: u.TypeSymb): Boolean

  def isFactoryMethod(tpe: u.TypeFull, decl: u.TypeSymb): Boolean
}
