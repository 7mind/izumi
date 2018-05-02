package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.CustomDef
import com.github.pshirshov.izumi.distage.model.references.WithDIKey
import com.github.pshirshov.izumi.distage.model.reflection.universe._

trait WithDIWiring {
  this: DIUniverseBase
    with WithDISafeType
    with WithDICallable
    with WithDIKey
    with WithDIAssociation
    with WithDIDependencyContext
  =>

  sealed trait Wiring {
    def associations: Seq[Association]
  }

  object Wiring {

    sealed trait UnaryWiring extends Wiring {
      def instanceType: TypeFull

      def associations: Seq[Association]
    }

    object UnaryWiring {

      sealed trait ProductWiring extends UnaryWiring

      case class Constructor(instanceType: TypeFull, associations: Seq[Association.Parameter]) extends ProductWiring

      case class AbstractSymbol(instanceType: TypeFull, associations: Seq[Association.AbstractMethod]) extends ProductWiring

      case class Function(provider: Provider, associations: Seq[Association]) extends UnaryWiring {
        override def instanceType: TypeFull = provider.ret
      }

      case class Instance(instanceType: TypeFull, instance: Any) extends UnaryWiring {
        override def associations: Seq[Association] = Seq.empty
      }

    }

    case class CustomWiring(customDef: CustomDef, associations: Seq[Association]) extends Wiring


    case class FactoryMethod(factoryType: TypeFull, factoryMethods: Seq[FactoryMethod.WithContext], fieldDependencies: Seq[Association.AbstractMethod]) extends Wiring {
      /**
        * this method returns product dependencies which aren't present in any signature of factory methods.
        * Though it's kind of a heuristic which can be spoiled at the time of plan initialization
        *
        * Complete check can only be performed at runtime.
        */
      override def associations: Seq[Association] = {
        val factoryMethodsArgs = factoryMethods.flatMap(_.methodArguments).toSet

        val productsDepsNotInMethods = factoryMethods.flatMap(_.wireWith.associations).filterNot(v => factoryMethodsArgs.contains(v.wireWith))

        productsDepsNotInMethods ++ fieldDependencies
      }
    }

    object FactoryMethod {

      case class WithContext(factoryMethod: MethodSymb, wireWith: UnaryWiring.ProductWiring, methodArguments: Seq[DIKey])

    }

  }

}
