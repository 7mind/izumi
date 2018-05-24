package com.github.pshirshov.izumi.distage.config

import com.github.pshirshov.izumi.distage.config.annotations.AutoConf
import com.github.pshirshov.izumi.distage.model.definition.With
import com.github.pshirshov.izumi.fundamentals.platform.build.ExposedTestScope

@ExposedTestScope
object ConfigFixtures {

  case class TestConf(flag: Boolean)

  case class ConcreteProduct(@AutoConf testConf: TestConf, int: Int)

  trait AbstractProduct

  case class AbstractProductImpl(@AutoConf testConf: TestConf) extends AbstractProduct

  trait FullyAbstractProduct {
    @AutoConf
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
    @AutoConf
    def testConf: T
  }

  trait TestGenericConfFactory[T] {
    def x: TestDependency

    def make(): FullyAbstractGenericConfProduct[T]
  }

  case class TestDependency(@AutoConf testConf: TestConf)

  trait TestTrait {
    def x: TestDependency

    @AutoConf
    def testConf: TestConf
  }

}
