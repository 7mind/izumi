package izumi.distage.model.reflection

import izumi.distage.model.reflection.universe.{DIUniverse, RuntimeDIUniverse}

trait DependencyKeyProvider {
  val u: DIUniverse

  def keyFromParameter(context: u.DependencyContext.ParameterContext, parameterSymbol: u.SymbolInfo): u.DIKey.BasicKey

  def associationFromParameter(parameterSymbol: u.SymbolInfo): u.Association.Parameter

  def keyFromMethod(context: u.DependencyContext.MethodContext, methodSymbol: u.SymbolInfo): u.DIKey.BasicKey

  def resultOfFactoryMethod(context: u.DependencyContext.MethodParameterContext): u.SafeType
}

object DependencyKeyProvider {

  trait Runtime extends DependencyKeyProvider {
    override val u: RuntimeDIUniverse.type = RuntimeDIUniverse
  }

  type Static[U] = Aux[U]

  type Aux[U] = DependencyKeyProvider { val u: U }

}
