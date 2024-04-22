package izumi.distage.model.reflection

import izumi.distage.model.reflection.universe.DIUniverse

trait ReflectionProvider {
  val u: DIUniverse
  import u._

  def parameterToAssociation(parameterSymbol: MacroSymbolInfo): Association.Parameter

  def constructorParameterLists(tpe: TypeNative): List[List[Association.Parameter]]
  def symbolToAnyWiring(tpe: TypeNative): MacroWiring
  def symbolToWiring(tpe: TypeNative): MacroWiring
  def zioHasParameters(transformName: String => String)(deepIntersection: List[TypeNative]): List[Association.Parameter]

  def isConcrete(tpe: TypeNative): Boolean
  def isWireableAbstract(tpe: TypeNative): Boolean
  def isFactory(tpe: TypeNative): Boolean
}

object ReflectionProvider {
  type Aux[U] = ReflectionProvider { val u: U }
}
