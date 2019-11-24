package izumi.distage.model.reflection

import izumi.distage.model.reflection.universe.{DIUniverse, RuntimeDIUniverse}

trait SymbolIntrospector {
  val u: DIUniverse

  case class SelectedConstructor(constructorSymbol: u.MethodSymb, arguments: List[List[u.SymbolInfo]])

  def hasConstructor(tpe: u.SafeType): Boolean
  def selectConstructorMethod(tpe: u.SafeType): Option[u.MethodSymb]
  def selectConstructor(tpe: u.SafeType): Option[SelectedConstructor]
  def selectNonImplicitParameters(symb: u.MethodSymb): List[List[u.Symb]]

  def isConcrete(tpe: u.TypeNative): Boolean
  def isWireableAbstract(tpe: u.TypeNative): Boolean
  def isFactory(tpe: u.TypeNative): Boolean
  def isWireableMethod(tpe: u.TypeNative, decl: u.Symb): Boolean
  def isFactoryMethod(tpe: u.TypeNative, decl: u.Symb): Boolean

  def findSymbolAnnotation(annType: u.TypeNative, symb: u.SymbolInfo): Option[u.u.Annotation]
}

object SymbolIntrospector {

  @deprecated("proxy runtime not wokr", "???")
  def canBeProxied[U <: DIUniverse](tpe: U#SafeType): Boolean = {
    !tpe.use(_.typeSymbol.isFinal)
  }

  trait Runtime extends SymbolIntrospector {
    override val u: RuntimeDIUniverse.type = RuntimeDIUniverse
  }

  type Static[U] = Aux[U]

  type Aux[U] = SymbolIntrospector {val u: U}

}
