package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.references.DIKey
import com.github.pshirshov.izumi.distage.model.util.Formattable
import com.github.pshirshov.izumi.fundamentals.reflection._


sealed trait Association extends Formattable {
  def wireWith: DIKey
}

object Association {

  case class Parameter(context: DependencyContext.ParameterContext, symbol: TypeSymb, wireWith: DIKey) extends Association {
    override def format: String = s"""par ${symbol.name}: ${symbol.info.typeSymbol.name} = lookup($wireWith)"""
  }

  case class Method(context: DependencyContext.MethodContext, symbol: TypeSymb, wireWith: DIKey) extends Association {
    override def format: String = s"""def ${symbol.info.typeSymbol.name}: ${symbol.info.resultType} = lookup($wireWith)"""
  }

}