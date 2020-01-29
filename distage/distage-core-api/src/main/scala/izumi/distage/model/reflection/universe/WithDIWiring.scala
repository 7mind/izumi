package izumi.distage.model.reflection.universe

trait WithDIWiring {
  this: DIUniverseBase
    with WithDISafeType
    with WithDIKey
    with WithDIAssociation
    with WithDISymbolInfo
  =>

  sealed trait Wiring
  object Wiring {
    sealed trait SingletonWiring extends Wiring {
      def prefix: Option[DIKey]
      def instanceType: TypeNative
      def associations: Seq[Association]
      def requiredKeys: Set[DIKey] = associations.map(_.key).toSet ++ prefix.toSet
    }
    object SingletonWiring {
      case class Class(instanceType: TypeNative, associations: List[Association.Parameter], prefix: Option[DIKey]) extends SingletonWiring
      case class Trait(instanceType: TypeNative, associations: List[Association.AbstractMethod], prefix: Option[DIKey]) extends SingletonWiring
    }

    case class Factory(factoryMethods: List[Factory.FactoryMethod], traitDependencies: List[Association.AbstractMethod]) extends Wiring {
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
      case class FactoryMethod(factoryMethod: SymbolInfo.Runtime, wireWith: SingletonWiring, methodArgumentKeys: Seq[DIKey]) {
        def objectGraphDeps: Seq[Association] = wireWith.associations.filterNot(methodArgumentKeys contains _.key)
      }
    }

  }

}
