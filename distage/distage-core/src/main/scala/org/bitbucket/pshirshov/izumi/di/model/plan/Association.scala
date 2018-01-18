package org.bitbucket.pshirshov.izumi.di.model.plan

import org.bitbucket.pshirshov.izumi.di.TypeSymb
import org.bitbucket.pshirshov.izumi.di.model.{DIKey, Formattable}


sealed trait Association extends Formattable {
  def wireWith: DIKey
}

object Association {

  case class Parameter(symbol: TypeSymb, wireWith: DIKey) extends Association {
    override def format: String = s"""${symbol.info.typeSymbol.name}: $symbol = lookup($wireWith)"""
  }

  case class Method(symbol: TypeSymb, wireWith: DIKey) extends Association {
    override def format: String = s"""def ${symbol.info.typeSymbol.name}: ${symbol.info.resultType} = lookup($wireWith)"""
  }

}