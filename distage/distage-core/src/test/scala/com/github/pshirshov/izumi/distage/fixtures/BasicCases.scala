package com.github.pshirshov.izumi.distage.fixtures

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.definition.Id
import com.github.pshirshov.izumi.fundamentals.platform.build.ExposedTestScope
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks

@ExposedTestScope
object BasicCases {

  object BasicCase1 {

    trait TestDependency0 {
      def boom(): Int = 1
    }

    class TestImpl0 extends TestDependency0

    trait NotInContext

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

    case class TestInstanceBinding(z: String = "TestValue")

    case class TestCaseClass2(a: TestInstanceBinding)

    trait JustTrait

    class Impl0 extends JustTrait

    class Impl1 extends JustTrait

    class Impl2 extends JustTrait

    class Impl3 extends JustTrait

    case class LocatorDependent(ref: Locator.LocatorRef)

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
      , @Id("com.github.pshirshov.izumi.distage.fixtures.basiccases.basiccase2.testdependency0") val fieldArgDependencyAutoname: TestDependency0
      , @Id("named.test") argDependency: => TestInstanceBinding
    ) {
      val x = argDependency
      val y = fieldArgDependency

      def correctWired(): Boolean = {
        argDependency.z.nonEmpty && fieldArgDependency.boom() == 1 && fieldArgDependencyAutoname.boom() == 1 && (fieldArgDependency ne fieldArgDependencyAutoname)
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

  object BadAnnotationsCase {
    def value = "xxx"
    trait TestDependency0

    class TestClass
    (
      @Id(value) val fieldArgDependency: TestDependency0
    )
  }

  object BasicCase4 {
    trait Dependency

    class TestClass(tyAnnDependency: Dependency @Id("special")) {
      Quirks.discard(tyAnnDependency)
    }

    case class ClassTypeAnnT[A, B](val x: A @Id("classtypeann1"), y: B @Id("classtypeann2"))
  }

}
