package izumi.distage.injector

import distage.*
import izumi.distage.fixtures.TraitCases.*
import izumi.distage.fixtures.TypesCases.*
import izumi.distage.model.PlannerInput
import izumi.fundamentals.platform.assertions.ScalatestGuards
import izumi.fundamentals.platform.language.{IzScala, ScalaRelease}
import org.scalatest.wordspec.AnyWordSpec

import scala.Ordering.Implicits.infixOrderingOps
import scala.annotation.nowarn
import scala.language.reflectiveCalls

@nowarn("msg=reflectiveSelectable")
class AdvancedTypesTest extends AnyWordSpec with MkInjector with ScalatestGuards {

  "support generics" in {
    import TypesCase1.*

    val definition = PlannerInput.everything(new ModuleDef {
      make[List[Dep]].named("As").from(List(DepA()))
      make[List[Dep]].named("Bs").from(List(DepB()))
      make[List[DepA]].from(List(DepA(), DepA(), DepA()))
      make[TestClass[DepA]]
    })

    val injector = mkInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    assert(context.get[List[Dep]]("As").forall(_.isInstanceOf[DepA]))
    assert(context.get[List[DepA]].forall(_.isInstanceOf[DepA]))
    assert(context.get[List[Dep]]("Bs").forall(_.isInstanceOf[DepB]))
    assert(context.get[TestClass[DepA]].inner == context.get[List[DepA]])
  }

  "support classes with typealiases" in {
    import TypesCase1.*

    val definition = PlannerInput.everything(new ModuleDef {
      make[DepA]
      make[TestClass2[TypeAliasDepA]]
    })

    val injector = mkInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    assert(context.get[TestClass2[TypeAliasDepA]].inner.isInstanceOf[TypeAliasDepA])
  }

  "support traits with typealiases" in {
    import TypesCase1.*

    val definition = PlannerInput.everything(new ModuleDef {
      make[DepA]
      makeTrait[TestTrait]
    })

    val injector = mkInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    assert(context.get[TestTrait].dep.isInstanceOf[TypeAliasDepA])
  }

  "type annotations in di keys do not result in different keys" in {
    import TraitCase2.*

    val definition = PlannerInput.everything(new ModuleDef {
      make[Dependency1 @Id("special")]
      makeTrait[Trait1]
    })

    val injector = mkInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    val instantiated = context.get[Dependency1]
    val instantiated1 = context.get[Dependency1 @Id("special")]

    assert(instantiated eq instantiated1)
  }

  "handle `with` types" in {
    import TypesCase3.*

    val definition = PlannerInput.everything(new ModuleDef {
      make[Dep]
      make[Dep2]
      make[Trait2 & Trait1].fromTrait[Trait6]
    })

    val injector = mkInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    val instantiated = context.get[Trait2 & Trait1]

    assert(instantiated.dep == context.get[Dep])
  }

  "light type tags can handle refinement & structural types" in {
    import TypesCase3.*

    val definition = PlannerInput.everything(new ModuleDef {
      make[Dep]
      make[Dep2]
      make[Trait1 { def dep: Dep2 }].fromTrait[Trait3[Dep2]]
      make[Trait1 { def dep: Dep }].fromTrait[Trait3[Dep]]
      make[{ def dep: Dep }].fromTrait[Trait6]
    })

    val injector = mkInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    val instantiated1 = context.get[Trait1 { def dep: Dep2 }]
    val instantiated2 = context.get[{ def dep: Dep }]
    val instantiated3 = context.get[Trait1 { def dep: Dep }]

    assert(instantiated1.dep == context.get[Dep2])
    assert(instantiated2.dep == context.get[Dep])
    assert(instantiated3.dep == context.get[Dep])
  }

  "handle function local type aliases" in {
    import TypesCase4.*

    class Definition[T: Tag] extends ModuleDef {
      make[Dep]
      make[Dep2]
      locally {
        type X[A] = Trait1[Dep, A]
        makeTrait[X[T]]
      }
    }

    val injector = mkInjector()
    val plan = injector.planUnsafe(PlannerInput.everything(new Definition[Dep2]))
    val context = injector.produce(plan).unsafeGet()

    val instantiated = context.get[Trait1[Dep, Dep2]]

    assert(instantiated.a == context.get[Dep])
    assert(instantiated.b == context.get[Dep2])
  }

  "light type tags can handle abstract structural refinement types" in {
    import TypesCase3.*

    class Definition[T >: Null: Tag, G <: T { def dep: Dep }: Tag: TraitConstructor] extends ModuleDef {
      make[Dep]
      make[T { def dep2: Dep }].from(() => null.asInstanceOf[T { def dep2: Dep }])
      make[T { def dep: Dep }].from(TraitConstructor[G])
    }

    val definition = PlannerInput.everything(new Definition[Trait1, Trait1])

    val injector = mkInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    val instantiated = context.get[Trait1 { def dep: Dep }]

    assert(instantiated.dep == context.get[Dep])

  }

  "handle abstract `with` types" in {
    import TypesCase3.*

    class Definition[T: Tag, G <: T & Trait1: Tag: TraitConstructor, C <: T & Trait4: Tag: TraitConstructor] extends ModuleDef {
      make[Dep]
      make[T & Trait4].from(TraitConstructor[C])
      make[T & Trait1].from(TraitConstructor[G])
    }

    val definition = PlannerInput.everything(new Definition[Trait3[Dep], Trait3[Dep], Trait5[Dep]])

    val injector = mkInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    brokenOnScala3 {
      val instantiated = context.get[Trait3[Dep] & Trait1]
      val instantiated2 = context.get[Trait3[Dep] & Trait4]

      assert(instantiated.dep == context.get[Dep])
      assert(instantiated.isInstanceOf[Trait1])
      assert(!instantiated.isInstanceOf[Trait4])

      assert(instantiated2.dep == context.get[Dep])
      assert(instantiated2.isInstanceOf[Trait1])
      assert(instantiated2.isInstanceOf[Trait4])
    }
  }

  "handle generic parameters in abstract `with` types" in {
    import TypesCase3.*

    class Definition[T <: Dep: Tag: ClassConstructor, K >: Trait5[T]: Tag] extends ModuleDef {
      make[T]
      make[Trait3[T] & K].fromTrait[Trait5[T]]
    }

    val definition = PlannerInput.everything(new Definition[Dep, Trait4])

    val injector = mkInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()
    val instantiated = context.get[Trait3[Dep] & Trait4]

    assert(instantiated.dep == context.get[Dep])
  }

  "support newtypes" in {
    import TypesCase5.*

    val definition = PlannerInput.everything(new ModuleDef {
      make[WidgetId].from(WidgetId(1))
      make[Dep]
    })

    val injector = mkInjector()
    val context = injector.produce(definition).unsafeGet()

    val instantiated1 = context.get[Dep]
    val instantiated2 = context.get[WidgetId]
    assert(instantiated1.widgetId == instantiated2)
  }

  "empty refinements are supported in class strategy" in {
    import TypesCase4.*

    val definition = PlannerInput.everything(new ModuleDef {
      make[Dep {}]
    })

    val injector = mkInjector()
    val context = injector.produce(definition).unsafeGet()

    assert(context.get[Dep {}] != null)
  }

  "support constant types in class strategy" in {
    assume(IzScala.scalaRelease >= ScalaRelease.`2_13`(0))
    brokenOnScala3 {
      assertCompiles(
        """
        new ModuleDef {
          make[5]
        }
      """
      )
    }
  }

  "regression test for https://github.com/7mind/izumi/issues/1523 Parameterization failure with Set of intersection type alias" in {
    import cats.effect.IO

    object x {
      type SrcContextProcessor[F[_]] = SrcProcessor & ContextProcessor[F]
    }
    import x.*
    trait SrcProcessor
    trait ContextProcessor[F[_]]

    def tag[F[_]: TagK] = Tag[SrcContextProcessor[F]]
    def setTag[F[_]: TagK] = Tag[Set[SrcContextProcessor[F]]]

    val tag1 = tag[IO].tag
    val tag2 = Tag[SrcContextProcessor[IO]].tag
    assert(tag1 == tag2)
    assert(setTag[IO].tag == Tag[Set[SrcContextProcessor[IO]]].tag)
  }

}
