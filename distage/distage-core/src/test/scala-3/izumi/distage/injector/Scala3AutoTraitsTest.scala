package izumi.distage.injector

import distage.{FactoryConstructor, ModuleDef, PlannerInput, TraitConstructor, With}
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

      // support by-name
      val traitCtor = TraitConstructor[ATrait].get
      val atrait = traitCtor.unsafeApply(Seq(TypedRef.byName(5))).asInstanceOf[ATrait]

      assert(atrait.toWireT == 5)
      assert(traitCtor.unsafeApply(Seq(TypedRef.byName(throw new RuntimeException("shoudln't be invoked")))).isInstanceOf[ATrait])

      val abstractClassCtor = TraitConstructor[AnAbstractClass].get
      val anAbstractClass = abstractClassCtor.unsafeApply(Seq(TypedRef(5), TypedRef.byName("abc"))).asInstanceOf[AnAbstractClass]

      assert(anAbstractClass.c == 5)
      assert(anAbstractClass.toWireAC == "abc")
      assert(abstractClassCtor.unsafeApply(Seq(TypedRef(5), TypedRef.byName(throw new RuntimeException("shoudln't be invoked")))).isInstanceOf[AnAbstractClass])
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
      assert(
        traitAbstractCtor.unsafeApply(Seq(TypedRef(5), TypedRef.byName(throw new RuntimeException("shoudln't be invoked")))).isInstanceOf[TraitExtendingAbstractClass]
      )
      assert(traitExtendingAbstractClass.isInstanceOf[AnAbstractClass])
    }

    "construct a complex trait with multiple constructors" in {
      val traitCtor = TraitConstructor[TraitInheritingAbstractClassAndTraitWithConstructor].get
      val atraitWithCtor =
        traitCtor.unsafeApply(Seq(TypedRef(1), TypedRef(2), TypedRef(3), TypedRef(4))).asInstanceOf[TraitInheritingAbstractClassAndTraitWithConstructor]

      assert(atraitWithCtor.a == 3)
      assert(atraitWithCtor.b == 4)
      assert(atraitWithCtor.c == 2)

// generated code:
//  ((a: scala.Int, c: scala.Int, `a₂`: scala.Int, b: scala.Int) =>
//    class TraitInheritingAbstractClassAndTraitWithConstructorAutoImpl
//      extends izumi.distage.fixtures.Scala3TraitCases.AbstractClassWithTraitWithConstructor(a, c)
//      with izumi.distage.fixtures.Scala3TraitCases.TraitWithAbstractClassStackedWithConstructor(`a₂`)
//      with izumi.distage.fixtures.Scala3TraitCases.StackedTraitWithConstructor(b)
//      with izumi.distage.fixtures.Scala3TraitCases.TraitInheritingAbstractClassAndTraitWithConstructor
    }

    "support trait type with param" in {
      TraitConstructor[ATraitWithTypeParam2[Int]]
    }

    "support trait refinement" in {
      assert(TraitConstructor[ATrait1 { def a: 5 }].get.unsafeApply(Seq(TypedRef.byName[5](5))).asInstanceOf[ATrait1].a == 5)
      assert(TraitConstructor[ATrait1 { val a: 5 }].get.unsafeApply(Seq(TypedRef.byName[5](5))).asInstanceOf[ATrait1].a == 5)
    }

    "support factories" in {
      import scala.reflect.Selectable.reflectiveSelectable

      val definition = PlannerInput.everything(new ModuleDef {
        makeFactory[FactoryTrait1]
        makeFactory[{
            type U = Object
            def makeConcreteDep(): T @distage.With[C2]
            def makeConcreteDep1(d: Int): T @With[C2]
          }
        ]
        make[C1]
        make[Int].fromValue(1)
        make[Number].fromValue(5)
        make[String].fromValue("abc")
      })
      val injector = mkInjector()
      val plan = injector.planUnsafe(definition)

      val context = injector.produce(plan).unsafeGet()

      val factory1 = context.get[FactoryTrait1]
      val factory2 = context.get[{
          type U = Object
          def makeConcreteDep(): T @distage.With[C2]
          def makeConcreteDep1(d: Int): T @With[C2]
        }
      ]

      val c1 = context.get[C1]
      val int = context.get[Int]

      assert(factory1.f1 ne null)
      assert(factory1.f1 ne factory1.f1)
      assert(factory1.f1.isInstanceOf[C2])
      assert(factory1.f1.asInstanceOf[C2].c eq c1)
      assert(factory1.f1.asInstanceOf[C2].d == int)

      assert(factory1.f2() ne null)
      assert(factory1.f2() ne factory1.f1)
      assert(factory1.f2() ne factory1.f2())
      assert(factory1.f2().isInstanceOf[C2])
      assert(factory1.f2().asInstanceOf[C2].c eq c1)
      assert(factory1.f2().asInstanceOf[C2].d == int)

      val newC1 = new C1()
      assert(factory1.f3(newC1).isInstanceOf[C2])
      assert(factory1.f3(newC1).asInstanceOf[C2].c eq newC1)
      assert(factory1.f3(newC1).asInstanceOf[C2].d == int)

      val c3 = factory1.f4(1)(2L, 3.0)
      assert(c3.isInstanceOf[C3])
      assert(c3.asInstanceOf[C3] == C3(1, 2L, 5, "abc")(3.0))
      assert(c3.asInstanceOf[C3].a5 == 3.0)

      assert(factory2.makeConcreteDep() ne null)
      assert(factory2.makeConcreteDep() ne factory2.makeConcreteDep())
      assert(factory2.makeConcreteDep().isInstanceOf[C2])

      assert(factory2.makeConcreteDep1(7) == C2(C1(), 7))
      assert(factory2.makeConcreteDep1(7).asInstanceOf[C2].c eq c1)
    }

    "support intersection types with trait constructors" in {
      import izumi.distage.fixtures.Scala3TraitCases.IntersectionCase.*
      val traitConstructor = TraitConstructor[Trait1 & Trait2 & Class1].get
      val traitIntersection = traitConstructor.unsafeApply(Seq(TypedRef(4), TypedRef(1), TypedRef(2), TypedRef(3), TypedRef(5))).asInstanceOf[Trait1 & Trait2 & Class1]

      assert(traitIntersection.dep1 == 1)
      assert(traitIntersection.dep2 == 2)
      assert(traitIntersection.dep3 == 3)
      assert(traitIntersection.dep4 == 4)
      assert(traitIntersection.dep5 == 5)
    }

    "support overriding lazy vals in auto-traits" in {
      val definition = PlannerInput.everything(new ModuleDef {
        makeTrait[ATraitWithALazyField]
        make[Int].fromValue(1)
      })

      val injector = mkInjector()
      val plan = injector.planUnsafe(definition)

      val context = injector.produce(plan).unsafeGet()

      assert(context.get[ATraitWithALazyField].lazyField == 1)
    }

  }

}
