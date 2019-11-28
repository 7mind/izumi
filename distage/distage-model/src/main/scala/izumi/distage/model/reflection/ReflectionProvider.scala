package izumi.distage.model.reflection

import java.lang.reflect.Modifier

import izumi.distage.model.reflection.universe.DIUniverse

trait ReflectionProvider {
  val u: DIUniverse

  // keyprovider
  def associationFromParameter(parameterSymbol: u.SymbolInfo): u.Association.Parameter
  def keyFromParameter(context: u.DependencyContext.ParameterContext, parameterSymbol: u.SymbolInfo): u.DIKey.BasicKey
  def keyFromMethod(context: u.DependencyContext.MethodContext, methodSymbol: u.SymbolInfo): u.DIKey.BasicKey

  // reflectionprovider
  def symbolToWiring(symbl: u.TypeNative): u.Wiring.PureWiring
  def constructorParameterLists(symbl: u.TypeNative): List[List[u.Association.Parameter]]

  // symbolintrospector
  case class SelectedConstructor(constructorSymbol: u.MethodSymbNative, arguments: List[List[u.SymbolInfo]])
  def selectConstructor(tpe: u.TypeNative): Option[SelectedConstructor]
  def selectNonImplicitParameters(symb: u.MethodSymbNative): List[List[u.SymbNative]]

  def isConcrete(tpe: u.TypeNative): Boolean
  def isWireableAbstract(tpe: u.TypeNative): Boolean
  def isFactory(tpe: u.TypeNative): Boolean
  def isWireableMethod(tpe: u.TypeNative, decl: u.SymbNative): Boolean
  def isFactoryMethod(tpe: u.TypeNative, decl: u.SymbNative): Boolean

  def findSymbolAnnotation(annType: u.TypeNative, symb: u.SymbolInfo): Option[u.u.Annotation]
}

object ReflectionProvider {
  type Aux[U] = ReflectionProvider { val u: U }

  @deprecated("proxy runtime not wokr", "???")
  private[distage] def canBeProxied[U <: DIUniverse](tpe: U#SafeType): Boolean = {
    !Modifier.isFinal(classOf[Any].getModifiers)
    println(s"Proxies are not supported! in $tpe")
    true
  }
}
