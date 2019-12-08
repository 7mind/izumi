package izumi.distage.model.plan.operations

import izumi.distage.model.references.WithDIKey
import izumi.distage.model.reflection.universe.{DIUniverseBase, WithDISafeType, WithDISymbolInfo}
import izumi.fundamentals.platform.language.Quirks._

trait WithDIWiring {
  this: DIUniverseBase
    with WithDISafeType
    with WithDIKey
    with WithDIAssociation
    with WithDISymbolInfo
  =>

  sealed trait Wiring {
    def instanceType: SafeType
    def associations: Seq[Association]
    def replaceKeys(f: Association => DIKey.BasicKey): Wiring

    def requiredKeys: Set[DIKey] = associations.map(_.key).toSet
  }

  object Wiring {
    sealed trait PureWiring extends Wiring {
      override def replaceKeys(f: Association => DIKey.BasicKey): PureWiring
    }

    sealed trait SingletonWiring extends PureWiring {
      def instanceType: SafeType
      override def replaceKeys(f: Association => DIKey.BasicKey): SingletonWiring
    }

    object SingletonWiring {
      case class Instance(instanceType: SafeType, instance: Any) extends SingletonWiring {
        override final def associations: Seq[Association] = Seq.empty

        override final def replaceKeys(f: Association => DIKey.BasicKey): Instance = { f.discard(); this }
      }
      case class Reference(instanceType: SafeType, key: DIKey, weak: Boolean) extends SingletonWiring {
        override final def associations: Seq[Association] = Seq.empty

        override final val requiredKeys: Set[DIKey] = super.requiredKeys ++ Set(key)
        override final def replaceKeys(f: Association => DIKey.BasicKey): Reference = { f.discard(); this }
      }

      // FIXME: remove runtime wiring types ???
      @deprecated("non-existent wiring", "???")
      sealed trait ReflectiveInstantiationWiring extends SingletonWiring {
        def prefix: Option[DIKey]

        override def replaceKeys(f: Association => DIKey.BasicKey): ReflectiveInstantiationWiring
        override final def requiredKeys: Set[DIKey] = super.requiredKeys ++ prefix.toSet
      }
      case class Constructor(instanceType: SafeType, associations: List[Association.Parameter], prefix: Option[DIKey]) extends ReflectiveInstantiationWiring {
        override final def replaceKeys(f: Association => DIKey.BasicKey): Constructor = this.copy(associations = this.associations.map(a => a.withKey(f(a))))
      }
      case class AbstractSymbol(instanceType: SafeType, associations: List[Association.AbstractMethod], prefix: Option[DIKey]) extends ReflectiveInstantiationWiring {
        override final def replaceKeys(f: Association => DIKey.BasicKey): AbstractSymbol = this.copy(associations = this.associations.map(a => a.withKey(f(a))))
      }
    }

    sealed trait MonadicWiring extends Wiring {
      def effectWiring: PureWiring
      def effectHKTypeCtor: SafeType
    }
    object MonadicWiring {
      case class Effect(instanceType: SafeType, effectHKTypeCtor: SafeType, effectWiring: PureWiring) extends MonadicWiring {
        override final def associations: Seq[Association] = effectWiring.associations
        override final def requiredKeys: Set[DIKey] = effectWiring.requiredKeys

        override final def replaceKeys(f: Association => DIKey.BasicKey): Effect = copy(effectWiring = effectWiring.replaceKeys(f))
      }

      case class Resource(instanceType: SafeType, effectHKTypeCtor: SafeType, effectWiring: PureWiring) extends MonadicWiring {
        override final def associations: Seq[Association] = effectWiring.associations
        override final def requiredKeys: Set[DIKey] = effectWiring.requiredKeys

        override final def replaceKeys(f: Association => DIKey.BasicKey): Resource = copy(effectWiring = effectWiring.replaceKeys(f))
      }
    }

    case class Factory(factoryType: SafeType, factoryMethods: List[Factory.FactoryMethod], fieldDependencies: List[Association.AbstractMethod]) extends PureWiring {
      /**
        * this method returns product dependencies which aren't present in any signature of factory methods.
        * Though it's a kind of a heuristic that can be spoiled at the time of plan initialization
        *
        * Complete check can only be performed at runtime.
        */
      override final def associations: Seq[Association] = {
        factorySuppliedProductDeps ++ fieldDependencies
      }

      final def factorySuppliedProductDeps: Seq[Association] = {
        val factoryMethodsArgs = factoryMethods.flatMap(_.methodArguments).toSet
        factoryMethods.flatMap(_.wireWith.associations).filterNot(factoryMethodsArgs contains _.key)
      }

      override final def instanceType: SafeType = factoryType

      override final def requiredKeys: Set[DIKey] = {
        super.requiredKeys ++ factoryMethods.flatMap(_.wireWith.prefix.toSeq).toSet
      }

      override final def replaceKeys(f: Association => DIKey.BasicKey): Factory =
        this.copy(
          fieldDependencies = this.fieldDependencies.map(a => a.withKey(f(a))),
          factoryMethods = this.factoryMethods.map(m => m.copy(wireWith = m.wireWith.replaceKeys(f))),
        )
    }

    object Factory {
      case class FactoryMethod(factoryMethod: SymbolInfo.Runtime, wireWith: SingletonWiring.ReflectiveInstantiationWiring, methodArguments: Seq[DIKey]) {
        def associationsFromContext: Seq[Association] = wireWith.associations.filterNot(methodArguments contains _.key)
      }
    }

  }

}
