package izumi.distage.reflection.macros.universe

import izumi.distage.reflection.macros.universe.impl.DIUniverse

trait ReflectionProvider {
  val u: DIUniverse
  import u.*

  def parameterToAssociation(parameterSymbol: MacroSymbolInfo): Association.Parameter

  def constructorParameterLists(tpe: TypeNative): List[List[Association.Parameter]]
  def symbolToAnyWiring(tpe: TypeNative): MacroWiring
  def symbolToWiring(tpe: TypeNative): MacroWiring
  def zioHasParameters(transformName: String => String)(deepIntersection: List[TypeNative]): List[Association.Parameter]

  def isConcrete(tpe: TypeNative): Boolean
  def isWireableAbstract(tpe: TypeNative): Boolean
  def isFactory(tpe: TypeNative): Boolean

  def selectConstructorMethod(tpe: TypeNative): Option[MethodSymbNative]
}

object ReflectionProvider {
  type Aux[U] = ReflectionProvider { val u: U }
}
