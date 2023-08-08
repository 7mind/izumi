package izumi.distage.injector

import distage.Id
import izumi.distage.compat.ZIOTest
import izumi.distage.constructors.ZEnvConstructor
import izumi.distage.fixtures.TraitCases.*
import izumi.distage.fixtures.TraitCases.TraitCase2.{Dependency1, Trait1}
import izumi.distage.fixtures.TypesCases.*
import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.reflection.TypedRef
import izumi.fundamentals.platform.assertions.ScalatestGuards
import org.scalatest.wordspec.AnyWordSpec
import zio.*

import scala.util.Try

class ZIOHasInjectionTest extends AnyWordSpec with MkInjector with ZIOTest with ScalatestGuards {

  type HasInt = Int
  type HasX[B] = B
  type HasIntBool = HasInt with HasX[Boolean]

  def trait1(d1: Dependency1) = new Trait1 { override def dep1: Dependency1 = d1 }

  // FIXME wtf
//  def getDep1[F[-_, +_, +_]: Ask3]: F[Has[Dependency1], Nothing, Dependency1] =
//    F.askWith((_: Has[Dependency1]).get)
//  def getDep2[F[-_, +_, +_]: Ask3]: F[Has[Dependency2], Nothing, Dependency2] =
//    F.askWith((_: Has[Dependency2]).get)
//
//  final class ResourceHasImpl[F[-_, +_, +_]: Local3](
//  ) extends Lifecycle.LiftF(for {
//      d1 <- getDep1
//      d2 <- getDep2
//    } yield new Trait2 { val dep1 = d1; val dep2 = d2 })
//
//  final class ResourceEmptyHasImpl[F[+_, +_]: Applicative2](
//    d1: Dependency1
//  ) extends Lifecycle.LiftF[F[Throwable, _], Trait1](
//      F.pure(trait1(d1))
//    )

  "ZEnvConstructor" should {

    "construct Has with tricky type aliases" in {
      val hasCtor = ZEnvConstructor[HasIntBool with Any].get

      val value = hasCtor.unsafeApply(Seq(TypedRef(5), TypedRef(false))).asInstanceOf[ZEnvironment[HasIntBool]]

      assert(value.get[Int] == 5)
      assert(value.get[Boolean] == false)
    }

    "handle empty Has (Any)" in {
      import TypesCase1.*

      val definition = new ModuleDef {
        make[Dep].from[DepA]
        make[TestClass2[Dep]].fromZEnv {
          (value: Dep) => ZIO.attempt(TestClass2(value))
        }
        make[TestClass2[Dep]].named("noargs").fromZEnv(ZIO.attempt(TestClass2(new DepA: Dep)))
      }

      val injector = mkNoCyclesInjector()
      val plan = injector.planUnsafe(PlannerInput.everything(definition))

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

    "handle one-arg fromZEnv" in {
      import TypesCase1.*

      val definition = new ModuleDef {
        make[Dep].from[DepA]
        make[TestClass2[Dep]].fromZEnv(ZIO.environmentWithZIO {
          (value: ZEnvironment[Dep]) =>
            ZIO.attempt(TestClass2(value.get))
        })
      }

      val injector = mkNoCyclesInjector()
      val plan = injector.planUnsafe(PlannerInput.everything(definition))

      val context = unsafeRun(injector.produceCustomF[Task](plan).unsafeGet())
      val instantiated = context.get[TestClass2[Dep]]
      assert(instantiated.isInstanceOf[TestClass2[Dep]])
      assert(instantiated.inner != null)
      assert(instantiated.inner eq context.get[Dep])
    }

    "progression test: since ZIO 2 can't support named bindings in zio.ZEnvironment type parameters" in brokenOnScala3 {
      import TypesCase1.*

      val ctorA: ZIO[Dep @Id("A"), Nothing, TestClass2[Dep]] = ZIO.serviceWith[Dep @Id("A")] {
        (value: Dep @Id("A")) => TestClass2(value)
      }
      val ctorB = ZIO.serviceWith[Dep @Id("B")] {
        (value: Dep @Id("B")) => TestClass2(value)
      }

      val definition = PlannerInput.everything(new ModuleDef {
        make[Dep].named("A").from[DepA]
        make[Dep].named("B").from[DepB]
        make[TestClass2[Dep]].named("A").fromZEnv[Dep @Id("A"), Nothing, TestClass2[Dep]](ctorA)
        make[TestClass2[Dep]].named("B").fromZEnv(ctorB)
      })

      val injector = mkInjector()
      val plan = injector.planUnsafe(definition)

      val t = Try {
        val context = unsafeRun(injector.produceCustomF[Task](plan).unsafeGet())

        val instantiated = context.get[TestClass2[Dep]]("A")
        assert(instantiated.inner.isInstanceOf[DepA])
        assert(!instantiated.inner.isInstanceOf[DepB])

        val instantiated1 = context.get[TestClass2[Dep]]("B")
        assert(instantiated1.inner.isInstanceOf[DepB])
        assert(!instantiated1.inner.isInstanceOf[DepA])
      }
      assert(t.failed.get.getMessage.contains("Found other bindings for the same type (did you forget to add or remove `@Id` annotation?)"))
    }

    "progression test: since ZIO 2 can't support multiple named bindings in zio.ZEnvironment without a type signature" in brokenOnScala3 {
      import TypesCase1.*

      val ctorAB = for {
        a <- ZIO.service[DepA @Id("A")]
        b <- ZIO.service[DepB @Id("B")]
      } yield TestClass3[Dep](a, b)

      val definition = PlannerInput.everything(new ModuleDef {
        make[DepA].named("A").from[DepA]
        make[DepB].named("B").from[DepB]
        make[TestClass3[Dep]].fromZEnv(ctorAB)
      })

      val injector = mkInjector()
      val plan = injector.planUnsafe(definition)

      val t = Try {
        val context = unsafeRun(injector.produceCustomF[Task](plan).unsafeGet())

        val instantiated = context.get[TestClass3[Dep]]
        assert(instantiated.a.isInstanceOf[DepA])
        assert(!instantiated.a.isInstanceOf[DepB])
        assert(instantiated.b.isInstanceOf[DepB])
        assert(!instantiated.b.isInstanceOf[DepA])
      }
      assert(t.failed.get.getMessage.contains("Found other bindings for the same type (did you forget to add or remove `@Id` annotation?)"))
    }

    "support multiple named bindings in zio.ZEnvironment with a type signature" in brokenOnScala3 {
      import TypesCase1.*

      val ctorAB: ZIO[(DepA @Id("A")) & (DepB @Id("B")), Nothing, TestClass3[Dep]] = for {
        a <- ZIO.service[DepA @Id("A")]
        b <- ZIO.service[DepB @Id("B")]
      } yield TestClass3[Dep](a, b)

      val definition = PlannerInput.everything(new ModuleDef {
        make[DepA].named("A").from[DepA]
        make[DepB].named("B").from[DepB]
        make[TestClass3[Dep]].fromZEnv(ctorAB)
      })

      val injector = mkInjector()
      val plan = injector.planUnsafe(definition)

      val context = unsafeRun(injector.produceCustomF[Task](plan).unsafeGet())

      val instantiated = context.get[TestClass3[Dep]]
      assert(instantiated.a.isInstanceOf[DepA])
      assert(!instantiated.a.isInstanceOf[DepB])
      assert(instantiated.b.isInstanceOf[DepB])
      assert(!instantiated.b.isInstanceOf[DepA])
    }

    // FIXME wtf specialize to ZIO?
//    "handle multi-parameter Has with mixed args & env injection and a refinement return" in {
//      import TraitCase2._
//
//      def getDep1 = ZIO.environmentWith[Dependency1](_.get)
//      def getDep2 = ZIO.environmentWith[Dependency2](_.get)
//
//      val definition = PlannerInput.everything(new ModuleDef {
//        make[Dependency1]
//        make[Dependency2]
//        make[Dependency3]
//        make[Trait3 { def dep1: Dependency1 }].fromZEnv(
//          (d3: Dependency3) =>
//            for {
//              d1 <- getDep1
//              d2 <- getDep2
//            } yield new Trait3 {
//              override val dep1 = d1
//              override val dep2 = d2
//              override val dep3 = d3
//            }
//        )
//        make[Trait2].fromZEnv(for {
//          d1 <- ZManaged.environmentWith[Dependency1](_.get)
//          d2 <- ZManaged.environmentWith[Dependency2](_.get)
//        } yield new Trait2 { val dep1 = d1; val dep2 = d2 })
////        make[Trait1].fromZEnv[Any, Nothing, Trait1] {
//        // FIXME: report bug - Dotty infers this as `make[Trait1].fromZEnv[Any, Any, Trait1]` - awful inference here wtf?
//        make[Trait1].fromZEnv {
//          (d1: Dependency1) =>
//            ZLayer.succeed(new Trait1 { val dep1 = d1 })
//        }
//
//        make[Trait2].named("classbased").fromZEnv[ResourceHasImpl[ZIO]]
//        make[Trait1].named("classbased").fromZEnv[ResourceEmptyHasImpl[IO]]
//
//        many[Trait2].addHas[ResourceHasImpl[ZIO]]
//        many[Trait1].addHas[ResourceEmptyHasImpl[IO]]
//
//        addImplicit[Applicative2[IO]]
//        addImplicit[Local3[ZIO]]
//      })
//
//      val injector = mkNoCyclesInjector()
//      val plan = injector.planUnsafe(definition)
//      val context = unsafeRun(injector.produceCustomF[Task](plan).unsafeGet())
//
//      val instantiated = context.get[Trait3 { def dep1: Dependency1 }]
//
//      assert(instantiated.dep1 eq context.get[Dependency1])
//      assert(instantiated.dep2 eq context.get[Dependency2])
//      assert(instantiated.dep3 eq context.get[Dependency3])
//
//      val instantiated1 = context.get[Trait3 { def dep1: Dependency1 }]
//      assert(instantiated1.dep2 eq context.get[Dependency2])
//
//      val instantiated10 = context.get[Trait2]
//      assert(instantiated10.dep2 eq context.get[Dependency2])
//
//      val instantiated2 = context.get[Trait1]
//      assert(instantiated2 ne null)
//
//      val instantiated3 = context.get[Trait2]("classbased")
//      assert(instantiated3.dep2 eq context.get[Dependency2])
//
//      val instantiated4 = context.get[Trait1]("classbased")
//      assert(instantiated4 ne null)
//
//      val instantiated5 = context.get[Set[Trait2]].head
//      assert(instantiated5.dep2 eq context.get[Dependency2])
//
//      val instantiated6 = context.get[Set[Trait1]].head
//      assert(instantiated6 ne null)
//    }

    // FIXME wtf specialize to ZIO?
//    "polymorphic ZIOHas injection" in {
//      import TraitCase2._
//
//      def definition[F[-_, +_, +_]: TagK3: Local3] = PlannerInput.everything(new ModuleDef {
//        make[Dependency1]
//        make[Dependency2]
//        make[Dependency3]
//        addImplicit[Local3[F]]
//        addImplicit[Applicative2[F[Any, +_, +_]]]
//        make[Trait3 { def dep1: Dependency1 }].fromZEnv(
//          (d3: Dependency3) =>
//            (for {
//              d1 <- getDep1
//              d2 <- getDep2
//            } yield new Trait3 {
//              override val dep1 = d1
//              override val dep2 = d2
//              override val dep3 = d3
//            }): F[Has[Dependency1] with Has[Dependency2], Nothing, Trait3]
//        )
//        make[Trait2].fromZEnv[ResourceHasImpl[F]]
//        make[Trait1].fromZEnv[ResourceEmptyHasImpl[F[Any, +_, +_]]]
//
//        many[Trait2].addHas[ResourceHasImpl[F]]
//        many[Trait1].addHas[ResourceEmptyHasImpl[F[Any, +_, +_]]]
//      })
//
//      val injector = mkNoCyclesInjector()
//      val plan = injector.planUnsafe(definition[ZIO])
//      val context = unsafeRun(injector.produceCustomF[Task](plan).unsafeGet())
//
//      val instantiated = context.get[Trait3 { def dep1: Dependency1 }]
//
//      assert(instantiated.dep1 eq context.get[Dependency1])
//      assert(instantiated.dep2 eq context.get[Dependency2])
//      assert(instantiated.dep3 eq context.get[Dependency3])
//
//      val instantiated1 = context.get[Trait3 { def dep1: Dependency1 }]
//      assert(instantiated1.dep2 eq context.get[Dependency2])
//
//      val instantiated10 = context.get[Trait2]
//      assert(instantiated10.dep2 eq context.get[Dependency2])
//
//      val instantiated2 = context.get[Trait1]
//      assert(instantiated2 ne null)
//
//      val instantiated3 = context.get[Set[Trait2]].head
//      assert(instantiated3.dep2 eq context.get[Dependency2])
//
//      val instantiated4 = context.get[Set[Trait1]].head
//      assert(instantiated4 ne null)
//    }

    "can handle AnyVals" in brokenOnScala3 {
      import TraitCase6.*

      val definition = PlannerInput.everything(new ModuleDef {
        make[Dep]
        make[AnyValDep]
        make[TestTrait].fromZEnv(
          ZIO.environmentWith[AnyValDep](
            h =>
              new TestTrait {
                override val anyValDep: AnyValDep = h.get
              }
          )
        )
      })

      val injector = mkInjector()
      val plan = injector.planUnsafe(definition)
      val context = unsafeRun(injector.produceCustomF[Task](plan).unsafeGet())

      assert(context.get[TestTrait].anyValDep ne null)
      // AnyVal reboxing happened
      assert(context.get[TestTrait].anyValDep ne context.get[AnyValDep].asInstanceOf[AnyRef])
      assert(context.get[TestTrait].anyValDep.d eq context.get[Dep])
    }

    "Scala 3 regression test: support more than 2 dependencies in ZEnvConstructor" in {
      trait OpenTracingService

      trait SttpBackend[F[_], +P]

      trait MyEndpoints[F[_, _]]

      trait ZioStreams
      trait WebSockets

      trait MyPublisher
      trait MyClient

      object MyPlugin extends ModuleDef {
        make[MyClient].fromZEnv[OpenTracingService with MyPublisher with SttpBackend[Task, ZioStreams with WebSockets] with MyEndpoints[IO], Nothing, MyClient] {
          ZIO.succeed(???): ZIO[OpenTracingService with MyPublisher with SttpBackend[Task, ZioStreams with WebSockets] with MyEndpoints[IO], Nothing, MyClient]
        }
      }
      val _ = MyPlugin
    }

  }

}
