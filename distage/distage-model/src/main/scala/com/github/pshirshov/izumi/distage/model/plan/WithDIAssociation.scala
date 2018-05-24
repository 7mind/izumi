package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.references.WithDIKey
import com.github.pshirshov.izumi.distage.model.reflection.universe._
import com.github.pshirshov.izumi.distage.model.util.Formattable

trait WithDIAssociation {
  this:  DIUniverseBase
    with WithDISafeType
    with WithDICallable
    with WithDIKey
    with WithDIDependencyContext
    with WithDISymbolInfo
  =>

  sealed trait Association extends Formattable {
    def wireWith: DIKey
    def symbol: SymbolInfo
  }

  object Association {
    case class Parameter(context: DependencyContext.ParameterContext, symbol: SymbolInfo, wireWith: DIKey) extends Association {
      override def format: String = s"""par ${symbol.name}: ${symbol.finalResultType} = lookup($wireWith)"""
    }

    case class AbstractMethod(context: DependencyContext.MethodContext, symbol: SymbolInfo.RuntimeSymbol, wireWith: DIKey) extends Association {
      override def format: String = s"""def ${symbol.name}: ${symbol.finalResultType} = lookup($wireWith)"""
    }

  }

}
