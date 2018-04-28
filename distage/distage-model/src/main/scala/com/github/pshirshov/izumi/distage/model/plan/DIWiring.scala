package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.CustomDef
import com.github.pshirshov.izumi.distage.model.references.DIKey
import com.github.pshirshov.izumi.distage.model.reflection.universe._

trait DIWiring {
  this: DIUniverseBase
    with DISafeType
    with DICallable
    with DIKey
    with DIAssociation
    with DIDependencyContext
    with DILiftableRuntimeUniverse
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
            { new $RuntimeDIUniverse.Wiring.UnaryWiring.Constructor($instanceType, ${associations.toList}) }
              """
          }
        }

        case class Abstract(instanceType: TypeFull, associations: Seq[Association.Method]) extends ProductWiring

      case class Function(provider: Provider, associations: Seq[Association]) extends UnaryWiring {
        override def instanceType: TypeFull = provider.ret
      }
      object Function {
        def apply(function: Provider): Function = {
          val associations = function.diKeys.map {
            key =>
              Association.Parameter(
                DependencyContext.CallableParameterContext(function)
                , key.symbol.tpe.typeSymbol.name.decodedName.toString
                , key.symbol
                , key
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


    case class FactoryMethod(factoryType: TypeFull, factoryMethods: Seq[FactoryMethod.WithContext], fieldDependencies: Seq[Association.Method]) extends Wiring {
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
