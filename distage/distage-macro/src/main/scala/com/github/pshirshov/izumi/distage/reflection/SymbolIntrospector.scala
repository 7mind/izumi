package com.github.pshirshov.izumi.distage.reflection
import com.github.pshirshov.izumi.distage.model.reflection.SelectedConstructor
import com.github.pshirshov.izumi.fundamentals.reflection._

trait SymbolIntrospector {

  def selectConstructor(symb: RuntimeUniverse.TypeFull): SelectedConstructor

  def selectParameters(symb: RuntimeUniverse.MethodSymb): List[RuntimeUniverse.TypeSymb]

  def isConcrete(symb: RuntimeUniverse.TypeFull): Boolean

  def isWireableAbstract(symb: RuntimeUniverse.TypeFull): Boolean

  def isFactory(symb: RuntimeUniverse.TypeFull): Boolean

  def isWireableMethod(tpe: RuntimeUniverse.TypeFull, decl: RuntimeUniverse.TypeSymb): Boolean

  def isFactoryMethod(tpe: RuntimeUniverse.TypeFull, decl: RuntimeUniverse.TypeSymb): Boolean
}
