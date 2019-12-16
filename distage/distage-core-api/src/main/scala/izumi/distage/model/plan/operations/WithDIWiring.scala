package izumi.distage.model.plan.operations

import izumi.distage.model.references.WithDIKey
import izumi.distage.model.reflection.universe.{DIUniverseBase, WithDISafeType, WithDISymbolInfo}

trait WithDIWiring {
  this: DIUniverseBase
    with WithDISafeType
    with WithDIKey
    with WithDIAssociation
    with WithDISymbolInfo
  =>
  object Wiring {
    sealed trait PureWiring

    object SingletonWiring {
      sealed trait ReflectiveInstantiationWiring extends PureWiring {
        def prefix: Option[DIKey]
        def instanceType: SafeType
        def associations: Seq[Association]
        def requiredKeys: Set[DIKey] = associations.map(_.key).toSet ++ prefix.toSet
      }
      case class Constructor(instanceType: SafeType, associations: List[Association.Parameter], prefix: Option[DIKey]) extends ReflectiveInstantiationWiring
      case class AbstractSymbol(instanceType: SafeType, associations: List[Association.AbstractMethod], prefix: Option[DIKey]) extends ReflectiveInstantiationWiring
    }

    case class Factory(factoryType: SafeType, factoryMethods: List[Factory.FactoryMethod], traitDependencies: List[Association.AbstractMethod]) extends PureWiring {

      final def factoryProductDepsFromObjectGraph: Seq[Association] = {
        import izumi.fundamentals.collections.IzCollections._
        val fieldKeys = traitDependencies.map(_.key).toSet

        factoryMethods
          .flatMap(_.objectGraphDeps)
          .distinctBy(_.key)
          .filterNot(fieldKeys contains _.key)
      }
    }

    object Factory {
      case class FactoryMethod(factoryMethod: SymbolInfo.Runtime, wireWith: SingletonWiring.ReflectiveInstantiationWiring, methodArgumentKeys: Seq[DIKey]) {
        def objectGraphDeps: Seq[Association] = wireWith.associations.filterNot(methodArgumentKeys contains _.key)
      }
    }

  }

}
