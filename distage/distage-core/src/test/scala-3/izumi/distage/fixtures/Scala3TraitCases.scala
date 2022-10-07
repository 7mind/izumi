package izumi.distage.fixtures

import izumi.distage.constructors.TraitConstructor
import izumi.distage.model.definition.With
import izumi.distage.model.reflection.TypedRef

object Scala3TraitCases {

  class AClass(a: String)

  trait ATrait {
    def toWireT: Int
    def xxx: Int = 1
  }

  abstract class AnAbstractClass(val c: Int) {
    def toWireAC: String
    //  def bullshit(a: Int): String
  }

  trait ATrait1 {
    def a: Int
  }
  class AClass1(override val a: Int) extends ATrait1 {}

  class AClass2(cparam: Int) extends AnAbstractClass(cparam) {
    val a = "xxx"
    override def toWireAC: String = a
  }

  trait TraitExtendingAbstractClass extends AnAbstractClass {
    val t = 1
  }

  trait ATraitWithTypeParam[A]

  trait ATraitWithConstructor(val c: Int)

  trait StackedTraitWithConstructor(val b: Int) extends ATraitWithConstructor with ATraitWithTypeParam[Int] { this: AbstractClassWithTraitWithConstructor => }

  abstract class AbstractClassWithTraitWithConstructor(val a: Int, c: Int) extends ATraitWithConstructor(c)

  trait TraitWithAbstractClassStackedWithConstructor(override val a: Int) extends AbstractClassWithTraitWithConstructor

  trait TraitInheritingAbstractClassAndTraitWithConstructor extends TraitWithAbstractClassStackedWithConstructor with StackedTraitWithConstructor

  trait T
  case class C1() extends T
  case class C2(c: C1, d: Int) extends T
  case class C3(a1: Int, a2: Long, a3: Number, a4: String)(a5: Double) extends T

  trait FactoryTrait1 {
//    def f1: T @With[C2]
//    def f2(): T @With[C2]
//    def f3(c: C1): T @With[C2]
    def f4(a1: Int)(a2: Long, g: Object, a5: Double): T @With[C3]
  }
}
