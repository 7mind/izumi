package izumi.distage.model.reflection.universe

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
        def instanceType: TypeNative
        def associations: Seq[Association]
        def requiredKeys: Set[DIKey] = associations.map(_.key).toSet ++ prefix.toSet
      }
      case class Constructor(instanceType: TypeNative, associations: List[Association.Parameter], prefix: Option[DIKey]) extends ReflectiveInstantiationWiring
      case class AbstractSymbol(instanceType: TypeNative, associations: List[Association.AbstractMethod], prefix: Option[DIKey]) extends ReflectiveInstantiationWiring
    }

    case class Factory(factoryMethods: List[Factory.FactoryMethod], traitDependencies: List[Association.AbstractMethod]) extends PureWiring {

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
