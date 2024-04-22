package izumi.distage.model.reflection.universe

import scala.annotation.nowarn

trait WithDIWiring { this: DIUniverseBase with WithDISafeType with WithDIKey with WithDIAssociation with WithDISymbolInfo =>

  sealed trait MacroWiring
  object MacroWiring {
    sealed trait MacroSingletonWiring extends MacroWiring {
      def prefix: Option[MacroDIKey]
      def instanceType: TypeNative
      def associations: Seq[Association]
      def requiredKeys: Set[MacroDIKey] = associations.map(_.key).toSet ++ prefix.toSet
    }
    object MacroSingletonWiring {
      case class Class(instanceType: TypeNative, classParameters: List[List[Association.Parameter]], prefix: Option[MacroDIKey]) extends MacroSingletonWiring {
        override lazy val associations: List[Association] = classParameters.flatten
      }
      case class Trait(instanceType: TypeNative, classParameters: List[List[Association.Parameter]], methods: List[Association.AbstractMethod], prefix: Option[MacroDIKey])
        extends MacroSingletonWiring {
        override lazy val associations: List[Association] = classParameters.flatten ++ methods
      }
    }

    case class Factory(factoryMethods: List[Factory.FactoryMethod], classParameters: List[List[Association.Parameter]], methods: List[Association.AbstractMethod])
      extends MacroWiring {
      @nowarn("msg=Unused import")
      final def factoryProductDepsFromObjectGraph: List[Association] = {
        import izumi.fundamentals.collections.IzCollections._
        val fieldKeys = methods.map(_.key).toSet

        factoryMethods
          .flatMap(_.objectGraphDeps)
          .distinctBy(_.key)
          .filterNot(fieldKeys contains _.key)
      }
    }

    object Factory {
      object WithProductDeps {
        def unapply(arg: Factory): Some[(List[Factory.FactoryMethod], List[List[Association.Parameter]], List[Association.AbstractMethod], List[Association])] = {
          Some((arg.factoryMethods, arg.classParameters, arg.methods, arg.factoryProductDepsFromObjectGraph))
        }
      }

      case class FactoryMethod(factoryMethod: MacroSymbolInfo.Runtime, wireWith: MacroSingletonWiring, methodArgumentKeys: Seq[MacroDIKey]) {
        def objectGraphDeps: Seq[Association] = wireWith.associations.filterNot(methodArgumentKeys contains _.key)
      }
    }

  }

}
