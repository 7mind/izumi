package izumi.distage.model.reflection

import java.lang.reflect.Modifier

import izumi.distage.model.reflection.universe.DIUniverse

trait ReflectionProvider {
  val u: DIUniverse
  import u._

//  val u0: scala.reflect.api.Universe
//  import u0.{Type => TypeNative}
//  import u0.{Symbol => SymbNative}

  // keyprovider
  def associationFromParameter(parameterSymbol: SymbolInfo): Association.Parameter

  // reflectionprovider
  def symbolToWiring(symbl: TypeNative): Wiring.PureWiring
  def constructorParameterLists(symbl: TypeNative): List[List[Association.Parameter]]

  // symbolintrospector
  def isConcrete(tpe: TypeNative): Boolean
  def isWireableAbstract(tpe: TypeNative): Boolean
  def isFactory(tpe: TypeNative): Boolean
}

object ReflectionProvider {
  type Aux[U] = ReflectionProvider { val u: U }
}
