package izumi.distage.model.reflection

import izumi.distage.model.reflection.universe.DIUniverse

trait ReflectionProvider {
  val u: DIUniverse
  import u._

  def associationFromParameter(parameterSymbol: SymbolInfo): Association.Parameter

  def symbolToWiring(symbl: TypeNative): Wiring
  def constructorParameterLists(symbl: TypeNative): List[List[Association.Parameter]]

  def isConcrete(tpe: TypeNative): Boolean
  def isWireableAbstract(tpe: TypeNative): Boolean
  def isFactory(tpe: TypeNative): Boolean
}

object ReflectionProvider {
  type Aux[U] = ReflectionProvider { val u: U }
}
