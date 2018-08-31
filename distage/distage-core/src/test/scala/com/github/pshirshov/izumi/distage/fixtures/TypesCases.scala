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

  object TypesCase3 {
    class Dep

    class Dep2 extends Dep

    trait Trait1 {
      def dep: Dep
    }

    trait Trait2 {
      def dep2: Dep2
    }

    trait Trait3[T <: Dep] extends Trait1 {
      def dep: T
    }

    trait Trait4

    trait Trait5[T <: Dep] extends Trait3[T] with Trait4 {
      def dep: T
    }

    trait Trait6 extends Trait2 with Trait1
  }

  object TypesCase4 {
    class Dep
    class Dep2

    trait Trait1[A, B] {
      def a: A
      def b: B
    }
  }

}
