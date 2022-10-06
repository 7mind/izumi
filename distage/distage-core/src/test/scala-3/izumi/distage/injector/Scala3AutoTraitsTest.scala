package izumi.distage.injector

import distage.With
import izumi.distage.constructors.{AnyConstructor, FactoryConstructor, TraitConstructor}
import izumi.distage.fixtures.Scala3TraitCases.*
import izumi.distage.model.reflection.TypedRef
import org.scalatest.wordspec.AnyWordSpec

import scala.language.reflectiveCalls

class Scala3AutoTraitsTest extends AnyWordSpec with MkInjector {

  "Scala 3 auto traits" should {

    "construct a basic trait" in {
      val classCtor1 = TraitConstructor[AClass1].get
      val aclass1 = classCtor1.unsafeApply(Seq(TypedRef(5))).asInstanceOf[AClass1]

      assert(aclass1.a == 5)

      val classCtor2 = TraitConstructor[AClass2].get
      val aclass2 = classCtor2.unsafeApply(Seq(TypedRef(5))).asInstanceOf[AClass2]

      assert(aclass2.c == 5)

      val traitCtor = TraitConstructor[ATrait].get
      val atrait = traitCtor.unsafeApply(Seq(TypedRef.byName(5))).asInstanceOf[ATrait]

      assert(atrait.toWireT == 5)
      assert(traitCtor.unsafeApply(Seq(TypedRef.byName(???))).isInstanceOf[ATrait])

      val abstractClassCtor = TraitConstructor[AnAbstractClass].get
      val anAbstractClass = abstractClassCtor.unsafeApply(Seq(TypedRef(5), TypedRef.byName("abc"))).asInstanceOf[AnAbstractClass]

      assert(anAbstractClass.c == 5)
      assert(anAbstractClass.toWireAC == "abc")
      assert(abstractClassCtor.unsafeApply(Seq(TypedRef(5), TypedRef.byName(???))).isInstanceOf[AnAbstractClass])
    }

    "construct a trait with constructor" in {
      val traitCtor = TraitConstructor[ATraitWithConstructor].get
      val atraitWithCtor = traitCtor.unsafeApply(Seq(TypedRef(5))).asInstanceOf[ATraitWithConstructor]

      assert(atraitWithCtor.c == 5)
    }

    "construct a trait extending abstract class" in {
      val traitAbstractCtor = TraitConstructor[TraitExtendingAbstractClass].get
      val traitExtendingAbstractClass = traitAbstractCtor.unsafeApply(Seq(TypedRef(5), TypedRef.byName("abc"))).asInstanceOf[TraitExtendingAbstractClass]

      assert(traitExtendingAbstractClass.c == 5)
      assert(traitExtendingAbstractClass.toWireAC == "abc")
      assert(traitAbstractCtor.unsafeApply(Seq(TypedRef(5), TypedRef.byName(???))).isInstanceOf[TraitExtendingAbstractClass])
      assert(traitExtendingAbstractClass.isInstanceOf[AnAbstractClass])
    }

    "construct a complex trait with multiple constructors" in {
      val traitCtor = TraitConstructor[TraitInheritingAbstractClassAndTraitWithConstructor].get
      val atraitWithCtor =
        traitCtor.unsafeApply(Seq(TypedRef(1), TypedRef(2), TypedRef(3), TypedRef(4))).asInstanceOf[TraitInheritingAbstractClassAndTraitWithConstructor]

      assert(atraitWithCtor.a == 3)
      assert(atraitWithCtor.b == 4)
      assert(atraitWithCtor.c == 2)

//  ((a: scala.Int, c: scala.Int, `a₂`: scala.Int, b: scala.Int) =>
//    class TraitInheritingAbstractClassAndTraitWithConstructorAutoImpl
//      extends izumi.distage.fixtures.Scala3TraitCases.AbstractClassWithTraitWithConstructor(a, c)
//      with izumi.distage.fixtures.Scala3TraitCases.TraitWithAbstractClassStackedWithConstructor(`a₂`)
//      with izumi.distage.fixtures.Scala3TraitCases.StackedTraitWithConstructor(b)
//      with izumi.distage.fixtures.Scala3TraitCases.TraitInheritingAbstractClassAndTraitWithConstructor
    }

    "support factories" in {
      FactoryConstructor[FactoryTrait1]

      FactoryConstructor[{
          type U = Object
          def makeConcreteDep(): T @With[C2]
          def makeConcreteDep1(): T @With[C2]
        }
      ]
    }
  }

}
