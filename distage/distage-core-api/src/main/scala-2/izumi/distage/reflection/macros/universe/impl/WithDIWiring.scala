package izumi.distage.reflection.macros.universe.impl

import izumi.distage.reflection.macros.universe.basicuniverse.MacroDIKey

import scala.annotation.nowarn

trait WithDIWiring { this: DIUniverseBase with WithDIAssociation with WithDISymbolInfo =>

  sealed trait MacroWiring
  object MacroWiring {
    sealed trait MacroSingletonWiring extends MacroWiring {
      protected def prefix: Option[MacroDIKey]
      def instanceType: TypeNative
      protected[WithDIWiring] def associations: Seq[Association]
      def requiredKeys: Set[MacroDIKey] = associations.map(_.key).toSet ++ prefix.toSet
    }
    object MacroSingletonWiring {
      case class Class(instanceType: TypeNative, classParameters: List[List[Association.Parameter]], prefix: Option[MacroDIKey]) extends MacroSingletonWiring {
        override lazy val associations: List[Association] = classParameters.flatten
      }
      case class Trait(
        instanceType: TypeNative,
        classParameters: List[List[Association.Parameter]],
        methods: List[Association.AbstractMethod],
        prefix: Option[MacroDIKey],
      ) extends MacroSingletonWiring {
        override lazy val associations: List[Association] = classParameters.flatten ++ methods
      }
    }

    case class Factory(factoryMethods: List[Factory.FactoryMethod], classParameters: List[List[Association.Parameter]], methods: List[Association.AbstractMethod])
      extends MacroWiring

    object Factory {
      @nowarn("msg=[Uu]nused import")
      def factoryProductDepsFromObjectGraph(factoryMethods: List[Factory.FactoryMethod]): List[Association] = {
        import izumi.fundamentals.collections.IzCollections._
        factoryMethods
          .flatMap(_.objectGraphDeps)
          .distinctBy(_.key)
      }

      case class FactoryMethod(factoryMethod: MacroSymbolInfo.Runtime, wireWith: MacroSingletonWiring, methodArgumentKeys: Seq[MacroDIKey]) {
        def objectGraphDeps: Seq[Association] = wireWith.associations.filterNot(methodArgumentKeys contains _.key)
      }
    }

  }

}
