package com.github.pshirshov.izumi.distage.fixtures

import com.github.pshirshov.izumi.distage.model.definition.Id
import com.github.pshirshov.izumi.fundamentals.platform.build.ExposedTestScope

@ExposedTestScope
object BasicCases {

  object BasicCase1 {

    trait TestDependency0 {
      def boom(): Int = 1
    }

    class TestImpl0 extends TestDependency0 {

    }

    trait NotInContext {}

    trait TestDependency1 {
      def unresolved: NotInContext
    }

    trait TestDependency3 {
      def methodDependency: TestDependency0

      def doSomeMagic(): Int = methodDependency.boom()
    }

    class TestClass
    (
      val fieldArgDependency: TestDependency0
      , argDependency: TestDependency1
    ) {
      val x = argDependency
      val y = fieldArgDependency
    }

    case class TestCaseClass(a1: TestClass, a2: TestDependency3)

    case class TestInstanceBinding(z: String =
                                         """R-r-rollin' down the window, white widow, fuck fame
Forest fire, climbin' higher, real life, it can wait""")

    case class TestCaseClass2(a: TestInstanceBinding)

    trait JustTrait {}

    class Impl0 extends JustTrait

    class Impl1 extends JustTrait

    class Impl2 extends JustTrait

    class Impl3 extends JustTrait

  }

  object BasicCase2 {

    trait TestDependency0 {
      def boom(): Int
    }

    class TestImpl0Good extends TestDependency0 {
      override def boom(): Int = 1
    }

    class TestImpl0Bad extends TestDependency0 {
      override def boom(): Int = 9
    }

    class TestClass
    (
      @Id("named.test.dependency.0") val fieldArgDependency: TestDependency0
      , @Id("named.test") argDependency: TestInstanceBinding
    ) {
      val x = argDependency
      val y = fieldArgDependency

      def correctWired(): Boolean = {
        fieldArgDependency.boom() == 1
      }
    }

    case class TestInstanceBinding(z: String =
                                         """R-r-rollin' down the window, white widow, fuck fame
Forest fire, climbin' higher, real life, it can wait""")

  }

  object BasicCase3 {

    trait Dependency

    class Impl1 extends Dependency

    class Impl2 extends Dependency

  }

}
