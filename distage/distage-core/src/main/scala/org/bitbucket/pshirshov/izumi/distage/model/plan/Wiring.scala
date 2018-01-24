package org.bitbucket.pshirshov.izumi.distage.model.plan

import org.bitbucket.pshirshov.izumi.distage.model.Callable
import org.bitbucket.pshirshov.izumi.distage.model.plan.Association.{Method, Parameter}
import org.bitbucket.pshirshov.izumi.distage.{CustomDef, MethodSymb, TypeFull, TypeSymb}

sealed trait Wiring {
  def associations: Seq[Association]
}

sealed trait UnaryWiring extends Wiring {
  def associations: Seq[Association]
}

object UnaryWiring {
  case class Constructor(instanceType: TypeFull, constructor: TypeSymb, associations: Seq[Parameter]) extends UnaryWiring

  case class Abstract(instanceType: TypeFull, associations: Seq[Method]) extends UnaryWiring

  case class Function(provider: Callable, associations: Seq[Association]) extends UnaryWiring

  case class Instance(instanceType: TypeFull, instance: Any) extends UnaryWiring {
    override def associations: Seq[Association] = Seq.empty
  }
}

object Wiring {
  case class CustomWiring(customDef: CustomDef, associations: Seq[Association]) extends Wiring


  case class FactoryMethod(factoryType: TypeFull, wirings: Seq[FactoryMethod.WithContext]) extends Wiring {
    override def associations: Seq[Association] = wirings.flatMap(_.wireWith.associations)
  }

  object FactoryMethod {
    case class WithContext(factoryMethod: MethodSymb, wireWith: UnaryWiring)
  }
}