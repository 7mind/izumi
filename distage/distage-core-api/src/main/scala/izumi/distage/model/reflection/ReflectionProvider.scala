package izumi.distage.model.reflection

import izumi.distage.model.reflection.universe.DIUniverse

trait ReflectionProvider {
  val u: DIUniverse
  import u._

  def parameterToAssociation(parameterSymbol: SymbolInfo): Association.Parameter

  def constructorParameterLists(tpe: TypeNative): List[List[Association.Parameter]]
  def symbolToWiring(tpe: TypeNative): Wiring
  def zioHasParameters(transformName: String => String)(tpe: TypeNative): List[Association.Parameter]

  def isConcrete(tpe: TypeNative): Boolean
  def isWireableAbstract(tpe: TypeNative): Boolean
  def isFactory(tpe: TypeNative): Boolean
}

object ReflectionProvider {
  type Aux[U] = ReflectionProvider { val u: U }
}
