package org.bitbucket.pshirshov.izumi.di.model.plan

import org.bitbucket.pshirshov.izumi.di.{CustomDef, TypeFull, TypeSymb}
import org.bitbucket.pshirshov.izumi.di.model.plan.Association.{Method, Parameter}
import org.bitbucket.pshirshov.izumi.di.model.{Callable, DIKey, Formattable}

sealed trait Wiring {
  def associations: Seq[Association]
}

sealed trait UnaryWiring extends Wiring {

}

object Wiring {

  case class Constructor(instanceType: TypeFull, constructor: TypeSymb, associations: Seq[Parameter]) extends UnaryWiring

  case class Abstract(instanceType: TypeFull, associations: Seq[Method]) extends UnaryWiring

  case class Function(provider: Callable, associations: Seq[Association]) extends UnaryWiring

  case class Instance(instanceType: TypeFull, instance: Any) extends UnaryWiring {
    override def associations: Seq[Association] = Seq.empty
  }

  case class CustomWiring(customDef: CustomDef, associations: Seq[Association]) extends Wiring {

  }

  case class Indirect(toWire: TypeSymb, wireWith: UnaryWiring)

  case class FactoryMethod(factoryType: TypeFull, wirings: Seq[Indirect]) extends Wiring {
    override def associations: Seq[Association] = wirings.flatMap(_.wireWith.associations)
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