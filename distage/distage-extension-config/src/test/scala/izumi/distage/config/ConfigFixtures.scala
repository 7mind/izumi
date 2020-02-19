package izumi.distage.config

import izumi.distage.model.definition.With

object ConfigFixtures {

  case class TestConf(flag: Boolean)

  case class ConcreteProduct(testConf: TestConf, int: Int)

  trait AbstractProduct

  case class AbstractProductImpl(testConf: TestConf) extends AbstractProduct

  trait FullyAbstractProduct {
    def testConf: TestConf
  }

  type TestConfAlias = TestConf

  trait TestFactory {
    def make(int: Int): ConcreteProduct

    def makeTrait(): FullyAbstractProduct

    @With[AbstractProductImpl]
    def makeTraitWith(): AbstractProduct
  }

  trait FullyAbstractGenericConfProduct[T] {
    def testConf: T
  }

  trait TestGenericConfFactory[T] {
    def x: TestDependency

    def make(): FullyAbstractGenericConfProduct[T]
  }

  case class TestDependency(testConf: TestConf)

  trait TestTrait {
    def x: TestDependency

    def testConf: TestConf
  }

}
