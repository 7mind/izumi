package izumi.distage.fixtures

import izumi.distage.model.definition.{Id, With}
import izumi.fundamentals.platform.build.ExposedTestScope

@ExposedTestScope
object FactoryCases {

  object FactoryCase1 {

    trait Dependency {
      def isSpecial: Boolean = false
      def isVerySpecial: Boolean = false

      override def toString: String = s"Dependency($isSpecial, $isVerySpecial)"
    }

    final case class ConcreteDep() extends Dependency

    final case class SpecialDep() extends Dependency {
      override def isSpecial: Boolean = true
    }

    final case class VerySpecialDep() extends Dependency {
      override def isVerySpecial: Boolean = true
    }

    final case class TestClass(b: Dependency)
    final case class AssistedTestClass(b: Dependency, a: Int)
    final case class NamedAssistedTestClass(@Id("special") b: Dependency, a: Int)
    final case class GenericAssistedTestClass[T, S](a: List[T], b: List[S], c: Dependency)

    trait Factory {
      def wiringTargetForDependency: Dependency
      def factoryMethodForDependency(): Dependency
      def x(): TestClass
    }

    trait MixedAssistendNonAssisted {
      def assisted(): TestClass
      def nonAssisted(dependency: Dependency): TestClass
    }

    trait OverridingFactory {
      def x(b: Dependency): TestClass
    }

    trait AssistedFactory {
      def x(a: Int): AssistedTestClass
    }

    abstract class AbstractClassFactory(private val t: TestClass) {
      def x(a: Int): AssistedTestClass
    }

    trait NamedAssistedFactory {
      def dep: Dependency @Id("veryspecial")
      def x(a: Int): NamedAssistedTestClass
    }

    trait GenericAssistedFactory {
      def x[T, S](t: List[T], s: List[S]): GenericAssistedTestClass[T, S]
    }

    trait AbstractDependency

    case class AbstractDependencyImpl(a: Dependency) extends AbstractDependency

    trait FullyAbstractDependency {
      def a: Dependency
    }

    trait AbstractFactory {
      @With[AbstractDependencyImpl]
      def x(): AbstractDependency
      def y(): FullyAbstractDependency
    }

    trait FactoryProducingFactory {
      def x(): Factory
    }

  }

  object FactoryCase2 {
    trait AbstractAbstractFactory {
      def x(z: Int, y: Int, x: Int): Product
    }

    trait AssistedAbstractFactory extends AbstractAbstractFactory {
      override def x(z: Int, y: Int, x: Int): Product @With[ProductImpl]
    }

    class Dependency()

    final case class ProductImpl(x: Int, y: Int, z: Int, dependency: Dependency)

    trait AssistedAbstractFactoryF[F[_]] extends AbstractAbstractFactory {
      override def x(z: Int, y: Int, x: Int): ProductF[F] @With[ProductFImpl[F]]
    }

    trait ProductF[F[_]] extends Product
    final case class ProductFImpl[F[_]](x: Int, y: Int, z: Int, dependency: F[Dependency]) extends ProductF[F]
  }

}
