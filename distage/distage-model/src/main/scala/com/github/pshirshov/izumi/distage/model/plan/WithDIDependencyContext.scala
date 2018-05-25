package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.reflection.universe._

trait WithDIDependencyContext {
  this: DIUniverseBase
    with WithDISafeType
    with WithDICallable
    with WithDISymbolInfo =>

  sealed trait DependencyContext {
    def definingClass: TypeFull
    def symbol: SymbolInfo
  }

  object DependencyContext {

    case class MethodContext(definingClass: TypeFull, methodSymbol: SymbolInfo.Runtime) extends DependencyContext {
      override def symbol: SymbolInfo = methodSymbol
    }

    sealed trait ParameterContext extends DependencyContext

    case class ConstructorParameterContext(definingClass: TypeFull, parameterSymbol: SymbolInfo) extends ParameterContext {
      override def symbol: SymbolInfo = parameterSymbol
    }

    case class MethodParameterContext(factoryClass: TypeFull, factoryMethod: SymbolInfo) extends ParameterContext {
      override def definingClass: TypeFull = factoryClass

      override def symbol: SymbolInfo = factoryMethod
    }

  }

}
