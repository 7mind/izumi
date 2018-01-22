package org.bitbucket.pshirshov.izumi.di.reflection
import org.bitbucket.pshirshov.izumi.di.{MethodSymb, TypeFull, TypeSymb}

trait SymbolIntrospector {

  def selectConstructor(symb: TypeFull): SelectedConstructor

  def selectParameters(symb: MethodSymb): List[TypeSymb]

  def isConcrete(symb: TypeFull): Boolean

  def isWireableAbstract(symb: TypeFull): Boolean

  def isFactory(symb: TypeFull): Boolean

  def isWireableMethod(tpe: TypeFull, decl: TypeSymb): Boolean

  def isFactoryMethod(tpe: TypeFull, decl: TypeSymb): Boolean
}
