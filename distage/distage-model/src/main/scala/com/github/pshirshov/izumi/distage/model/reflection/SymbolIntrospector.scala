package com.github.pshirshov.izumi.distage.model.reflection

import com.github.pshirshov.izumi.distage.model.reflection.universe.{DIUniverse, RuntimeDIUniverse}

trait SymbolIntrospector {
  val u: DIUniverse

  case class SelectedConstructor(constructorSymbol: u.MethodSymb, arguments: List[u.Symb])

  def selectConstructor(tpe: u.TypeFull): SelectedConstructor

  def selectParameters(symb: u.MethodSymb): List[u.Symb]

  def isConcrete(tpe: u.TypeFull): Boolean

  def isWireableAbstract(tpe: u.TypeFull): Boolean

  def isFactory(tpe: u.TypeFull): Boolean

  def isWireableMethod(tpe: u.TypeFull, decl: u.Symb): Boolean

  def isFactoryMethod(tpe: u.TypeFull, decl: u.Symb): Boolean

  def findSymbolAnnotation(annType: u.TypeFull, symb: u.Symb): Option[u.u.Annotation]

  def findTypeAnnotation(annType: u.TypeFull, tpe: u.TypeFull): Option[u.u.Annotation]
}

object SymbolIntrospector {

  trait Runtime extends SymbolIntrospector {
    override val u: RuntimeDIUniverse.type = RuntimeDIUniverse
  }

  type Static[U] = Aux[U]

  type Aux[U] = SymbolIntrospector { val u: U }

}
