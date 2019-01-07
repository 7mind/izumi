package com.github.pshirshov.izumi.distage.fixtures

import com.github.pshirshov.izumi.distage.model.definition.Id
import com.github.pshirshov.izumi.fundamentals.platform.build.ExposedTestScope

@ExposedTestScope
object TraitCases {

  object TraitCase1 {

    class Dependency1

    trait TestTrait {
      def dep: Dependency1
    }

  }

  object TraitCase2 {

    class Dependency1 {
      override def toString: String = "Hello World"
    }

    class Dependency2

    class Dependency3

    trait Trait1 {
      protected def dep1: Dependency1
    }

    trait Trait2 extends Trait1 {
      override protected def dep1: Dependency1

      def dep2: Dependency2
    }

    trait Trait3 extends Trait1 with Trait2 {
      def dep3: Dependency3

      def prr(): String = dep1.toString
    }

  }

  object TraitCase3 {

    trait ATraitWithAField {
      def method: Int = 1

      val field: Int = 1
    }

  }

  object TraitCase4 {

    trait Dep {
      def isA: Boolean
    }

    class DepA extends Dep {
      override def isA: Boolean = true
    }

    class DepB extends Dep {
      override def isA: Boolean = false
    }

    trait Trait {
      @Id("A")
      def depA: Dep

      @Id("B")
      def depB: Dep
    }

    trait Trait1 {
      def depA: Dep @Id("A")

      def depB: Dep @Id("B")
    }

  }

  object TraitCase5 {

    final case class Dep()

    trait TestTrait {
      protected val dep: Dep
      def rd: String = {
        dep.toString
      }
    }
  }

  object TraitCase6 {
    final case class Dep()

    final case class AnyValDep(d: Dep) extends AnyVal

    trait TestTrait {
      def anyValDep: AnyValDep
    }
  }

}
