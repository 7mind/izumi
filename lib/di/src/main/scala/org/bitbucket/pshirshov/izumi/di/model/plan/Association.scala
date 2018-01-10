package org.bitbucket.pshirshov.izumi.di.model.plan

import org.bitbucket.pshirshov.izumi.di.Symb
import org.bitbucket.pshirshov.izumi.di.model.{DIKey, Formattable}


sealed trait Association extends Formattable {
  def wireWith: DIKey
}

object Association {

  case class Parameter(symbol: Symb, wireWith: DIKey) extends Association {
    override def format: String = s"""${symbol.name}: ${symbol.info} = lookup($wireWith)"""
  }

  case class Method(symbol: Symb, wireWith: DIKey) extends Association {
    override def format: String = s"""def ${symbol.name}: ${symbol.info.resultType} = lookup($wireWith)"""
  }

}