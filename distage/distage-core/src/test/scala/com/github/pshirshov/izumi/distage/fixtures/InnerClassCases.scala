package com.github.pshirshov.izumi.distage.fixtures

import com.github.pshirshov.izumi.fundamentals.platform.build.ExposedTestScope

@ExposedTestScope
object InnerClassCases {

  object InnerClassCase1 {

    class TestModule {
      class TestDependency
      class TestClass(val a: TestDependency)
    }

  }

  object InnerClassByNameCase {

    class TestModule {
      class TestDependency
      class TestClass(a:  => TestDependency) {
        def aValue: TestDependency = a
      }

    }

  }

  object InnerClassCase2 {

    object StableObjectInheritingTrait extends TestTrait
    object StableObjectInheritingTrait1 extends TestTrait

    trait TestTrait {

      case class TestDependency()

      case class TestClass(testDependency: TestDependency)

      trait TestFactory {
        def mk(testDependency: TestDependency): TestClass
      }

    }

  }

}
