package com.github.pshirshov.izumi.distage.reflection
import com.github.pshirshov.izumi.distage.model.reflection.SelectedConstructor
import com.github.pshirshov.izumi.fundamentals.reflection._

trait SymbolIntrospector {

  def selectConstructor(symb: TypeFull): SelectedConstructor

  def selectParameters(symb: MethodSymb): List[TypeSymb]

  def isConcrete(symb: TypeFull): Boolean

  def isWireableAbstract(symb: TypeFull): Boolean

  def isFactory(symb: TypeFull): Boolean

  def isWireableMethod(tpe: TypeFull, decl: TypeSymb): Boolean

  def isFactoryMethod(tpe: TypeFull, decl: TypeSymb): Boolean
}
