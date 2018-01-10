package org.bitbucket.pshirshov.izumi.di.plan

import org.bitbucket.pshirshov.izumi.di.Symb
import org.bitbucket.pshirshov.izumi.di.model.DIKey

// traits: typeTag[T2].tpe.decls.head.getClass
// classes: typeTag[C3].tpe.decls.last.info.paramLists
// names are available: typeTag[C3].tpe.decls.last.info.paramLists.head.head.name
trait Formattable {
  def format: String
}

sealed trait Association extends Formattable{
  def wireWith: DIKey
}

object Association {

  case class Parameter(symbol: Symb, wireWith: DIKey) extends Association {
    override def format: String = s"""${symbol.name} = lookup($wireWith)"""
  }

  case class Method(symbol: Symb, wireWith: DIKey) extends Association {
    override def format: String = s"""def ${symbol.name}: ${symbol.info.resultType} = lookup($wireWith)"""
  }

}