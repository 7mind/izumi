package com.github.pshirshov.izumi.distage.fixtures

import com.github.pshirshov.izumi.fundamentals.platform.build.ExposedTestScope

@ExposedTestScope
object ImplicitCases {

  object ImplicitCase1 {

    class MyDummyImplicit extends DummyImplicit {
      def imADummy: Boolean = true
    }

    class Dep

    case class TestClass(dep: Dep)(implicit val dummyImplicit: DummyImplicit)

  }

  object ImplicitCase2 {
    class TestDependency1
    class TestDependency2
    class TestDependency3

    class TestClass(val a: TestDependency1)(val b: TestDependency3, implicit val c: TestDependency2)(implicit val d: TestDependency3)
  }

}
