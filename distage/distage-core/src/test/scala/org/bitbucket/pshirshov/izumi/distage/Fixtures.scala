package org.bitbucket.pshirshov.izumi.distage

import org.bitbucket.pshirshov.izumi.distage.definition.{Id, With}

object Case1 {

  trait TestDependency0 {
    def boom(): Int = 1
  }

  class TestImpl0 extends TestDependency0 {

  }

  trait NotInContext {}

  trait TestDependency1 {
    // TODO: plan API to let user provide importDefs
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
    def boom(): Int = 1
  }

  class TestClass
  (
    @Id("named.test.dependency.0") val fieldArgDependency: TestDependency0
    , @Id("named.test") argDependency: TestInstanceBinding
  ) {
    val x = argDependency
    val y = fieldArgDependency
  }

  class TestImpl0 extends TestDependency0 {

  }

  case class TestInstanceBinding(z: String =
                                 """R-r-rollin' down the window, white widow, fuck fame
Forest fire, climbin' higher, real life, it can wait""")

}

object Case2 {

  trait Circular1 {
    def arg: Circular2
  }

  class Circular2(arg: Circular1)

}

object Case3 {
//
//  trait A {
//    println("A")
//  }
//
//  trait B {
//    println("B")
//  }
//
//  trait C extends A with B {
//    println("C")
//  }

  trait Circular1 {
    def arg: Circular2
  }

  trait Circular2 {
    def arg: Circular3
  }


  trait Circular3 {
    ???
    def x(a: Object): Unit = {true}
    def b(): Boolean = {this.x(this); true}
    def arg: Circular4

    def arg2: Circular5

    def method: Long = 2L
  }

  trait Circular4 {
    ???
    def arg: Circular1
    def testVal: Int = 1
  }

  trait Circular5 {
    def arg: Circular1

    def arg2: Circular4
  }

}

object Case4 {

  trait Dependency

  class Impl1 extends Dependency

  class Impl2 extends Dependency

}


object Case5 {

  trait Dependency

  class TestClass(b: Dependency)

  class AssistedTestClass(val a: Int, b: Dependency)

  trait Factory {
    def x(): TestClass
  }

  trait OverridingFactory {
    def x(b: Dependency): TestClass
  }

  trait AssistedFactory {
    def x(a: Int): TestClass
  }

  trait AbstractDependency
  class AbstractDependencyImpl extends AbstractDependency

  trait AbstractFactory {
    @With[AbstractDependencyImpl]()
    def x(): AbstractDependency
  }


}

object Case6 {

  trait Dependency1

  trait Dependency1Sub extends Dependency1

  class TestClass(b: Dependency1)

  class TestClass2(a: TestClass)

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
    override def dep1: Dependency1
    def dep2: Dependency2
  }

  trait Trait3 extends Trait1 with Trait2 {
    def dep3: Dependency3

    def prr: Unit = println(dep1.toString)
  }

}
