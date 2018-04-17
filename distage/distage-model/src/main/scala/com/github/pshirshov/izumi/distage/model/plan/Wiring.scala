package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.CustomDef
import com.github.pshirshov.izumi.distage.model.references.DIKey
import com.github.pshirshov.izumi.distage.model.reflection.universe.{Callable, DIUniverseBase, RuntimeUniverse, SafeType}

trait Wiring {
  this: DIUniverseBase
    with SafeType
    with Callable
    with DIKey
    with Association
    with DependencyContext
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

      import u._

      sealed trait ProductWiring extends UnaryWiring

        case class Constructor(instanceType: TypeFull, associations: Seq[Association.Parameter]) extends ProductWiring
        object Constructor {
          implicit final val liftableConstructor: Liftable[Constructor] = {
            case Constructor(instanceType, associations) => q"""
            { new ${symbolOf[RuntimeUniverse.type].asClass.module}.Wiring.UnaryWiring.Constructor($instanceType, ${associations.toList}) }
              """
          }
        }

        case class Abstract(instanceType: TypeFull, associations: Seq[Association.Method]) extends ProductWiring

      case class Function(provider: Callable, associations: Seq[Association]) extends UnaryWiring {
        override def instanceType: TypeFull = provider.ret
      }
      object Function {
        def apply(function: Callable): Function = {
          val associations = function.argTypes.map {
            parameter =>
              Association.Parameter(
                DependencyContext.CallableParameterContext(function)
                , parameter.tpe.typeSymbol.name.decodedName.toString
                , SafeType(parameter.tpe)
                , DIKey.TypeKey(parameter)
              )
          }
          UnaryWiring.Function(function, associations)
        }
      }

      case class Instance(instanceType: TypeFull, instance: Any) extends UnaryWiring {
        override def associations: Seq[Association] = Seq.empty
      }

    }

    case class CustomWiring(customDef: CustomDef, associations: Seq[Association]) extends Wiring


    case class FactoryMethod(factoryType: TypeFull, wireables: Seq[FactoryMethod.WithContext], dependencies: Seq[Association.Method]) extends Wiring {
      /**
        * this method returns product dependencies which aren't present in any signature of factory methods.
        * Though it's kind of a heuristic which can be spoiled at the time of plan initialization
        *
        * Complete check can only be performed at runtime.
        */
      override def associations: Seq[Association] = {
        val signature = wireables.flatMap(_.methodArguments).toSet
        wireables.flatMap(_.wireWith.associations).filterNot(v => signature.contains(v.wireWith))
      }
    }

    object FactoryMethod {

      case class WithContext(factoryMethod: MethodSymb, wireWith: UnaryWiring.ProductWiring, methodArguments: Seq[DIKey])

    }

  }

}
