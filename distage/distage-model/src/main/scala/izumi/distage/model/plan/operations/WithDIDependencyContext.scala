package izumi.distage.model.plan.operations

import izumi.distage.model.reflection.universe.{DIUniverseBase, WithDICallable, WithDISafeType, WithDISymbolInfo}

trait WithDIDependencyContext {
  this: DIUniverseBase
    with WithDISafeType
    with WithDICallable
    with WithDISymbolInfo =>

  sealed trait DependencyContext {
    def definingClass: SafeType
    def symbol: SymbolInfo
  }

  object DependencyContext {

    case class MethodContext(definingClass: SafeType, methodSymbol: SymbolInfo.Runtime) extends DependencyContext {
      override def symbol: SymbolInfo = methodSymbol
    }

    sealed trait ParameterContext extends DependencyContext

    case class ConstructorParameterContext(definingClass: SafeType, parameterSymbol: SymbolInfo) extends ParameterContext {
      override def symbol: SymbolInfo = parameterSymbol
    }

    case class MethodParameterContext(factoryClass: SafeType, factoryMethod: SymbolInfo) extends ParameterContext {
      override def definingClass: SafeType = factoryClass

      override def symbol: SymbolInfo = factoryMethod
    }

  }

}
