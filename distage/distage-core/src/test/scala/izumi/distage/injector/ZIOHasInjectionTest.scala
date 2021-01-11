package izumi.distage.injector

import distage.{Id, TagK3}
import izumi.distage.constructors.HasConstructor
import izumi.distage.fixtures.TraitCases.TraitCase2.{Dependency1, Dependency2, Trait1, Trait2}
import izumi.distage.fixtures.TraitCases._
import izumi.distage.fixtures.TypesCases._
import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.{Lifecycle, ModuleDef}
import izumi.distage.model.reflection.TypedRef
import izumi.functional.bio.{Applicative2, Ask3, F, Local3}
import org.scalatest.exceptions.TestFailedException
import org.scalatest.wordspec.AnyWordSpec
import zio.Runtime.default.unsafeRun
import zio._

class ZIOHasInjectionTest extends AnyWordSpec with MkInjector {

  type HasInt = Has[Int]
  type HasX[B] = Has[B]
  type HasIntBool = HasInt with HasX[Boolean]

  def trait1(d1: Dependency1) = new Trait1 { override protected def dep1: Dependency1 = d1 }

  def getDep1[F[-_, +_, +_]: Ask3]: F[Has[Dependency1], Nothing, Dependency1] =
    F.askWith((_: Has[Dependency1]).get)
  def getDep2[F[-_, +_, +_]: Ask3]: F[Has[Dependency2], Nothing, Dependency2] =
    F.askWith((_: Has[Dependency2]).get)

  final class ResourceHasImpl[F[-_, +_, +_]: Local3](
  ) extends Lifecycle.LiftF(for {
      d1 <- getDep1
      d2 <- getDep2
    } yield new Trait2 { val dep1 = d1; val dep2 = d2 })

  final class ResourceEmptyHasImpl[F[+_, +_]: Applicative2](
    d1: Dependency1
  ) extends Lifecycle.LiftF[F[Throwable, ?], Trait1](
      F.pure(trait1(d1))
    )

  "HasConstructor" should {

    "construct Has with tricky type aliases" in {
      val hasCtor = HasConstructor[HasIntBool with Any].get

      val value = hasCtor.unsafeApply(Seq(TypedRef(5), TypedRef(false))).asInstanceOf[HasIntBool]

      assert(value.get[Int] == 5)
      assert(value.get[Boolean] == false)
    }

    "handle empty Has (Any)" in {
      import TypesCase1._

      val definition = new ModuleDef {
        make[Dep].from[DepA]
        make[TestClass2[Dep]].fromHas {
          value: Dep => ZIO(TestClass2(value))
        }
        make[TestClass2[Dep]].named("noargs").fromHas(ZIO(TestClass2(new DepA: Dep)))
      }

      val error = intercept[TestFailedException](
        assertCompiles(
          """new ModuleDef {
             make[TestClass2[Dep]].fromHas { value: Has[Dep] =>
               ZIO(TestClass2(value.get)) : ZIO[Int, Throwable, TestClass2[Dep]]
             }
           }"""
        )
      )
      assert(error.getMessage contains "intersection contains type constructors that aren't")
      assert(error.getMessage contains "Int")

      val injector = mkNoCyclesInjector()
      val plan = injector.plan(PlannerInput.everything(definition))

      val context = unsafeRun(injector.produceCustomF[Task](plan).unsafeGet())

      val instantiated1 = context.get[TestClass2[Dep]]
      assert(instantiated1.isInstanceOf[TestClass2[Dep]])
      assert(instantiated1.inner != null)
      assert(instantiated1.inner eq context.get[Dep])

      val instantiated2 = context.get[TestClass2[Dep]]("noargs")
      assert(instantiated2.isInstanceOf[TestClass2[Dep]])
      assert(instantiated2.inner != null)
      assert(instantiated2.inner ne context.get[Dep])
    }

    "handle one-arg fromHas" in {
      import TypesCase1._

      val definition = new ModuleDef {
        make[Dep].from[DepA]
        make[TestClass2[Dep]].fromHas(ZIO.accessM {
          value: Has[Dep] =>
            ZIO(TestClass2(value.get))
        })
      }

      val injector = mkNoCyclesInjector()
      val plan = injector.plan(PlannerInput.everything(definition))

      val context = unsafeRun(injector.produceCustomF[Task](plan).unsafeGet())
      val instantiated = context.get[TestClass2[Dep]]
      assert(instantiated.isInstanceOf[TestClass2[Dep]])
      assert(instantiated.inner != null)
      assert(instantiated.inner eq context.get[Dep])
    }

    "support named bindings in zio.Has type parameters" in {
      import TypesCase1._

      val ctorA = ZIO.accessM {
        value: Has[Dep @Id("A")] => ZIO(TestClass2(value.get))
      }
      val ctorB = ZIO.accessM {
        value: Has[Dep @Id("B")] => ZIO(TestClass2(value.get))
      }

      val definition = PlannerInput.everything(new ModuleDef {
        make[Dep].named("A").from[DepA]
        make[Dep].named("B").from[DepB]
        make[TestClass2[Dep]].named("A").fromHas(ctorA)
        make[TestClass2[Dep]].named("B").fromHas(ctorB)
      })

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = unsafeRun(injector.produceCustomF[Task](plan).unsafeGet())

      val instantiated = context.get[TestClass2[Dep]]("A")
      assert(instantiated.inner.isInstanceOf[DepA])
      assert(!instantiated.inner.isInstanceOf[DepB])

      val instantiated1 = context.get[TestClass2[Dep]]("B")
      assert(instantiated1.inner.isInstanceOf[DepB])
      assert(!instantiated1.inner.isInstanceOf[DepA])
    }

    "handle multi-parameter Has with mixed args & env injection and a refinement return" in {
      import TraitCase2._

      def getDep1 = ZIO.access[Has[Dependency1]](_.get)
      def getDep2 = ZIO.access[Has[Dependency2]](_.get)

      val definition = PlannerInput.everything(new ModuleDef {
        make[Dependency1]
        make[Dependency2]
        make[Dependency3]
        make[Trait3 { def dep1: Dependency1 }].fromHas(
          (d3: Dependency3) =>
            for {
              d1 <- getDep1
              d2 <- getDep2
            } yield new Trait3 {
              override val dep1 = d1
              override val dep2 = d2
              override val dep3 = d3
            }
        )
        make[Trait2].fromHas(for {
          d1 <- ZManaged.access[Has[Dependency1]](_.get)
          d2 <- ZManaged.access[Has[Dependency2]](_.get)
        } yield new Trait2 { val dep1 = d1; val dep2 = d2 })
        make[Trait1].fromHas {
          d1: Dependency1 =>
            ZLayer.succeed(new Trait1 { val dep1 = d1 })
        }

        make[Trait2].named("classbased").fromHas[ResourceHasImpl[ZIO]]
        make[Trait1].named("classbased").fromHas[ResourceEmptyHasImpl[IO]]

        many[Trait2].addHas[ResourceHasImpl[ZIO]]
        many[Trait1].addHas[ResourceEmptyHasImpl[IO]]

        addImplicit[Applicative2[IO]]
        addImplicit[Local3[ZIO]]
      })

      val injector = mkNoCyclesInjector()
      val plan = injector.plan(definition)
      val context = unsafeRun(injector.produceCustomF[Task](plan).unsafeGet())

      val instantiated = context.get[Trait3 { def dep1: Dependency1 }]

      assert(instantiated.dep1 eq context.get[Dependency1])
      assert(instantiated.dep2 eq context.get[Dependency2])
      assert(instantiated.dep3 eq context.get[Dependency3])

      val instantiated1 = context.get[Trait3 { def dep1: Dependency1 }]
      assert(instantiated1.dep2 eq context.get[Dependency2])

      val instantiated10 = context.get[Trait2]
      assert(instantiated10.dep2 eq context.get[Dependency2])

      val instantiated2 = context.get[Trait1]
      assert(instantiated2 ne null)

      val instantiated3 = context.get[Trait2]("classbased")
      assert(instantiated3.dep2 eq context.get[Dependency2])

      val instantiated4 = context.get[Trait1]("classbased")
      assert(instantiated4 ne null)

      val instantiated5 = context.get[Set[Trait2]].head
      assert(instantiated5.dep2 eq context.get[Dependency2])

      val instantiated6 = context.get[Set[Trait1]].head
      assert(instantiated6 ne null)
    }

    "polymorphic ZIOHas injection" in {
      import TraitCase2._

      def definition[F[-_, +_, +_]: TagK3: Local3] = PlannerInput.everything(new ModuleDef {
        make[Dependency1]
        make[Dependency2]
        make[Dependency3]
        addImplicit[Local3[F]]
        addImplicit[Applicative2[F[Any, +?, +?]]]
        make[Trait3 { def dep1: Dependency1 }].fromHas(
          (d3: Dependency3) =>
            (for {
              d1 <- getDep1
              d2 <- getDep2
            } yield new Trait3 {
              override val dep1 = d1
              override val dep2 = d2
              override val dep3 = d3
            }): F[Has[Dependency1] with Has[Dependency2], Nothing, Trait3]
        )
        make[Trait2].fromHas[ResourceHasImpl[F]]
        make[Trait1].fromHas[ResourceEmptyHasImpl[F[Any, +?, +?]]]

        many[Trait2].addHas[ResourceHasImpl[F]]
        many[Trait1].addHas[ResourceEmptyHasImpl[F[Any, +?, +?]]]
      })

      val injector = mkNoCyclesInjector()
      val plan = injector.plan(definition[ZIO])
      val context = unsafeRun(injector.produceCustomF[Task](plan).unsafeGet())

      val instantiated = context.get[Trait3 { def dep1: Dependency1 }]

      assert(instantiated.dep1 eq context.get[Dependency1])
      assert(instantiated.dep2 eq context.get[Dependency2])
      assert(instantiated.dep3 eq context.get[Dependency3])

      val instantiated1 = context.get[Trait3 { def dep1: Dependency1 }]
      assert(instantiated1.dep2 eq context.get[Dependency2])

      val instantiated10 = context.get[Trait2]
      assert(instantiated10.dep2 eq context.get[Dependency2])

      val instantiated2 = context.get[Trait1]
      assert(instantiated2 ne null)

      val instantiated3 = context.get[Set[Trait2]].head
      assert(instantiated3.dep2 eq context.get[Dependency2])

      val instantiated4 = context.get[Set[Trait1]].head
      assert(instantiated4 ne null)
    }

    "can handle AnyVals" in {
      import TraitCase6._

      val definition = PlannerInput.everything(new ModuleDef {
        make[Dep]
        make[AnyValDep]
        make[TestTrait].fromHas(
          ZIO.access[Has[AnyValDep]](
            h =>
              new TestTrait {
                override val anyValDep: AnyValDep = h.get
              }
          )
        )
      })

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = unsafeRun(injector.produceCustomF[Task](plan).unsafeGet())

      assert(context.get[TestTrait].anyValDep != null)
      // AnyVal reboxing happened
      assert(context.get[TestTrait].anyValDep ne context.get[AnyValDep].asInstanceOf[AnyRef])
      assert(context.get[TestTrait].anyValDep.d eq context.get[Dep])
    }

  }

}
