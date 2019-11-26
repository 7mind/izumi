package izumi.distage.model.reflection

import izumi.distage.model.reflection.universe.{DIUniverse, RuntimeDIUniverse}

trait SymbolIntrospector {
  val u: DIUniverse

  case class SelectedConstructor(constructorSymbol: u.MethodSymb, arguments: List[List[u.SymbolInfo]])

  def hasConstructor(tpe: u.SafeType): Boolean
  def selectConstructorMethod(tpe: u.SafeType): Option[u.MethodSymb]
  def selectConstructor(tpe: u.SafeType): Option[SelectedConstructor]
  def selectNonImplicitParameters(symb: u.MethodSymb): List[List[u.Symb]]

  def isConcrete(tpe: u.SafeType): Boolean
  def isWireableAbstract(tpe: u.SafeType): Boolean
  def isFactory(tpe: u.SafeType): Boolean
  def isWireableMethod(tpe: u.SafeType, decl: u.Symb): Boolean
  def isFactoryMethod(tpe: u.SafeType, decl: u.Symb): Boolean

  def findSymbolAnnotation(annType: u.SafeType, symb: u.SymbolInfo): Option[u.u.Annotation]
  def findTypeAnnotation(annType: u.SafeType, tpe: u.SafeType): Option[u.u.Annotation]

  def canBeProxied(tpe: u.SafeType): Boolean = {
    !tpe.use(_.typeSymbol.isFinal)
  }

  def hasByNameParameter(sym: u.MethodSymb): Boolean = {
    sym.paramLists.exists(_.exists(v => v.isTerm && v.asTerm.isByNameParam))
  }
}

object SymbolIntrospector {

  trait Runtime extends SymbolIntrospector {
    override val u: RuntimeDIUniverse.type = RuntimeDIUniverse
  }

  type Static[U] = Aux[U]

  type Aux[U] = SymbolIntrospector { val u: U }

}
