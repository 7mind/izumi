package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.model.definition.{Id, With}

import scala.util.Random

object Fixtures {

  object Case1 {

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

  object Case1_1 {

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

  object Case2 {

    trait Circular1 {
      def arg: Circular2
    }

    class Circular2(val arg: Circular1)
  }

  object Case3 {

    trait Circular1 {
      def arg: Circular2
    }

    trait Circular2 {
      def arg: Circular3
    }


    trait Circular3 {
      def arg: Circular4

      def arg2: Circular5

      def method: Long = 2L
    }

    trait Circular4 {
      def arg: Circular1

      def testVal: Int = 1
    }

    trait Circular5 {
      def arg: Circular1

      def arg2: Circular4
    }

    trait CircularBad1 {
      def arg: CircularBad2

      def bad() = {
        if (Random.nextInt(10) < 100) {
          throw new RuntimeException()
        }
      }

      bad()
    }

    trait CircularBad2 {
      def arg: CircularBad1

      def bad() = {
        if (Random.nextInt(10) < 100) {
          throw new RuntimeException()
        }
      }

      bad()
    }

  }

  object Case4 {

    trait Dependency

    class Impl1 extends Dependency

    class Impl2 extends Dependency

  }


  object Case5 {

    trait Dependency {
      def isSpecial: Boolean = false
    }

    case class ConcreteDep() extends Dependency

    case class SpecialDep() extends Dependency {
      override def isSpecial: Boolean = true
    }

    case class TestClass(b: Dependency)

    case class AssistedTestClass(a: Int, b: Dependency)

    case class NamedAssistedTestClass(a: Int, @Id("special") b: Dependency)

    case class GenericAssistedTestClass[T, S](a: List[T], b: List[S], c: Dependency)

    trait Factory {
      def wiringTargetForDependency: Dependency

      def factoryMethodForDependency(): Dependency

      def x(): TestClass
    }

    trait OverridingFactory {
      def x(b: Dependency): TestClass
    }

    trait AssistedFactory {
      def x(a: Int): AssistedTestClass
    }

    trait NamedAssistedFactory {
      def x(a: Int): NamedAssistedTestClass
    }

    trait GenericAssistedFactory {
      def x[T, S](t: List[T], s: List[S]): GenericAssistedTestClass[T, S]
    }

    trait AbstractDependency

    class AbstractDependencyImpl extends AbstractDependency

    trait AbstractFactory {
      @With[AbstractDependencyImpl]
      def x(): AbstractDependency
    }


  }

  object Case6 {

    trait Dependency1

    trait Dependency1Sub extends Dependency1

    class TestClass(val b: Dependency1)

    class TestClass2(val a: TestClass)

  }

  object Case7 {

    class Dependency1

    trait TestTrait {
      def dep: Dependency1
    }

  }

  object Case8 {

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

  object Case9 {

    trait ATraitWithAField {
      def method: Int = 1

      val field: Int = 1
    }

  }

  object Case10 {

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

  }

  object Case11 {

    trait Dep

    case class DepA() extends Dep

    case class DepB() extends Dep

    type TypeAliasDepA = DepA

    case class TestClass[D](inner: List[D])

    case class TestClass2[D](inner: D)

    trait TestTrait {
      def dep: TypeAliasDepA
    }

  }

  object Case12 {

    class Dep()

    case class Parameterized[T](t: T)

    trait ParameterizedTrait[T] {
      def t: T
    }

  }

  object Case13 {

    class MyDummyImplicit extends DummyImplicit {
      def imADummy: Boolean = true
    }

    class Dep

    case class TestClass(dep: Dep)(implicit val dummyImplicit: DummyImplicit)

  }

  object Case14 {

    case class Dep()

    trait TestTrait {
      protected val dep: Dep
      def rd: String = {
        dep.toString
      }
    }
  }
}
