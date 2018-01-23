package org.bitbucket.pshirshov.izumi.distage.model.plan

import org.bitbucket.pshirshov.izumi.distage.TypeSymb
import org.bitbucket.pshirshov.izumi.distage.model.{DIKey, Formattable}
import org.bitbucket.pshirshov.izumi.distage.reflection.DependencyContext


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