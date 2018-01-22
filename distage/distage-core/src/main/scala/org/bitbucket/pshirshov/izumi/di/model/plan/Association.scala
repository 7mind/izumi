package org.bitbucket.pshirshov.izumi.di.model.plan

import org.bitbucket.pshirshov.izumi.di.{TypeFull, TypeSymb}
import org.bitbucket.pshirshov.izumi.di.model.plan.Association.{Method, Parameter}
import org.bitbucket.pshirshov.izumi.di.model.{Callable, DIKey, Formattable}

sealed trait Wireable {
  def associations: Seq[Association]
}

sealed trait UnaryWireable extends Wireable {

}

object Wireable {

  case class Constructor(instanceType: TypeFull, constructor: TypeSymb, associations: Seq[Parameter]) extends UnaryWireable

  case class Abstract(instanceType: TypeFull, associations: Seq[Method]) extends UnaryWireable

  case class Function(provider: Callable, associations: Seq[Association]) extends UnaryWireable

  case class Empty() extends UnaryWireable {
    override def associations: Seq[Association] = Seq.empty
  }

  case class Indirect(toWire: TypeSymb, wireWith: UnaryWireable) extends UnaryWireable {
    override def associations: Seq[Association] = wireWith.associations
  }
  
  case class FactoryMethod(factoryType: TypeFull, wireables: Seq[UnaryWireable]) extends Wireable {
    override def associations: Seq[Association] = wireables.flatMap(_.associations)
  }

}

sealed trait Association extends Formattable {
  def wireWith: DIKey
}

object Association {

  case class Parameter(symbol: TypeSymb, wireWith: DIKey) extends Association {
    override def format: String = s"""par ${symbol.name}: ${symbol.info.typeSymbol.name} = lookup($wireWith)"""
  }

  case class Method(symbol: TypeSymb, wireWith: DIKey) extends Association {
    override def format: String = s"""def ${symbol.info.typeSymbol.name}: ${symbol.info.resultType} = lookup($wireWith)"""
  }

}