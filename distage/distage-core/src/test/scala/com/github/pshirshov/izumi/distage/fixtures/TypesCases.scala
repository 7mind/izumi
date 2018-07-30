package com.github.pshirshov.izumi.distage.fixtures

import com.github.pshirshov.izumi.fundamentals.platform.build.ExposedTestScope

@ExposedTestScope
object TypesCases {

  object TypesCase1 {

    trait Dep

    final case class DepA() extends Dep

    final case class DepB() extends Dep

    type TypeAliasDepA = DepA

    final case class TestClass[D](inner: List[D])

    final case class TestClass2[D](inner: D)

    trait TestTrait {
      def dep: TypeAliasDepA
    }

  }

  object TypesCase2 {

    class Dep()

    final case class Parameterized[T](t: T)

    trait ParameterizedTrait[T] {
      def t: T
    }

  }

}
