package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.CustomDef
import com.github.pshirshov.izumi.distage.model.functions.Callable
import com.github.pshirshov.izumi.distage.model.plan.Association.{Method, Parameter}
import com.github.pshirshov.izumi.distage.model.references.DIKey
import com.github.pshirshov.izumi.fundamentals.reflection._

sealed trait Wiring {
  def associations: Seq[Association]
}


object Wiring {
  sealed trait UnaryWiring extends Wiring {
    def associations: Seq[Association]
  }

  object UnaryWiring {
    case class Constructor(instanceType: RuntimeUniverse.TypeFull, constructor: RuntimeUniverse.TypeNative, associations: Seq[Parameter]) extends UnaryWiring

    case class Abstract(instanceType: RuntimeUniverse.TypeFull, associations: Seq[Method]) extends UnaryWiring

    case class Function(provider: Callable, associations: Seq[Association]) extends UnaryWiring

    case class Instance(instanceType: RuntimeUniverse.TypeFull, instance: Any) extends UnaryWiring {
      override def associations: Seq[Association] = Seq.empty
    }
  }
  
  case class CustomWiring(customDef: CustomDef, associations: Seq[Association]) extends Wiring


  case class FactoryMethod(factoryType: RuntimeUniverse.TypeFull, wirings: Seq[FactoryMethod.WithContext], dependencies: Seq[Method]) extends Wiring {
    /**
      * this method returns factory dependencies which don't present in any signature of factory methods.
      * Though it's kind of a heuristic which can be spoiled at the time of plan initialization
      *
      * Complete check can only be performed at runtime.
      */
    override def associations: Seq[Association] = {
      val signature  = wirings.flatMap(_.signature).toSet
      wirings.flatMap(_.wireWith.associations).filterNot(v => signature.contains(v.wireWith))
    }
  }

  object FactoryMethod {
    case class WithContext(factoryMethod: RuntimeUniverse.MethodSymb, wireWith: UnaryWiring, signature: Seq[DIKey])
  }
}