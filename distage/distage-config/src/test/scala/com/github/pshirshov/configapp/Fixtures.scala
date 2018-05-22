package com.github.pshirshov.configapp

import com.github.pshirshov.izumi.distage.config.annotations.AutoConf
import com.github.pshirshov.izumi.fundamentals.platform.build.ExposedTestScope

@ExposedTestScope
object Fixtures {

  object FactoryCase {
    case class TestConf(flag: Boolean)
    case class TestClass(@AutoConf testConf: TestConf, int: Int)
    trait TestFactory {
      def make(int: Int): TestClass
    }
  }

}
