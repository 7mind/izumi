package izumi.distage.fixtures

import izumi.fundamentals.platform.build.ExposedTestScope

@ExposedTestScope
object InnerClassCases {

  object InnerClassUnstablePathsCase {
    class TestModule {
      case class TestDependency()

      case class TestClass(a: TestDependency)

      trait TestFactory {
        def mk(testDependency: TestDependency): TestClass
      }

      case class Circular1(circular2: Circular2)
      case class Circular2(circular1: Circular1)
    }
  }

  object InnerClassByNameCase {
    class TestModule {
      class TestDependency

      class TestClass(a: => TestDependency) {
        def aValue: TestDependency = a
      }
    }
  }

  object InnerClassStablePathsCase {

    object StableObjectInheritingTrait extends TestTrait
    object StableObjectInheritingTrait1 extends TestTrait

    trait TestTrait {

      case class TestDependency()

      case class TestClass(testDependency: TestDependency)

      trait TestFactory {
        def mk(testDependency: TestDependency): TestClass
      }

      case class Circular1(circular2: Circular2)
      case class Circular2(circular2: Circular1)

      class ByNameCircular1(circular20: => ByNameCircular2) {
        def circular2: ByNameCircular2 = circular20
      }
      class ByNameCircular2(circular20: => ByNameCircular1) {
        def circular2: ByNameCircular1 = circular20
      }
    }

  }

}
