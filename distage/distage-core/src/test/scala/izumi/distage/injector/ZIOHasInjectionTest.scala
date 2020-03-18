package izumi.distage.injector

import distage.Id
import izumi.distage.constructors.HasConstructor
import izumi.distage.fixtures.TypesCases._
import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.reflection.TypedRef
import org.scalatest.exceptions.TestFailedException
import org.scalatest.wordspec.AnyWordSpec
import zio.Runtime.default.unsafeRun
import zio.{Has, Task, ZIO}

class ZIOHasInjectionTest extends AnyWordSpec with MkInjector {

  type HasInt = Has[Int]
  type HasX[B] = Has[B]
  type HasIntBool = HasInt with HasX[Boolean]

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
        make[TestClass2[Dep]].fromHas { value: Dep => ZIO(TestClass2(value)) }
        make[TestClass2[Dep]].named("noargs").fromHas { ZIO(TestClass2(new DepA: Dep)) }
      }

      val error = intercept[TestFailedException](assertCompiles(
        """new ModuleDef {
             make[TestClass2[Dep]].fromHas { value: Has[Dep] =>
               ZIO(TestClass2(value.get)) : ZIO[Int, Throwable, TestClass2[Dep]]
             }
           }"""
      ))
      assert(error.getMessage contains "intersection contains type constructors that aren't")
      assert(error.getMessage contains "Int")

      val injector = mkNoCyclesInjector()
      val plan = injector.plan(PlannerInput.noGc(definition))

      val context = unsafeRun(injector.produceF[Task](plan).unsafeGet())

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
      val plan = injector.plan(PlannerInput.noGc(definition))

      val context = unsafeRun(injector.produceF[Task](plan).unsafeGet())
      val instantiated = context.get[TestClass2[Dep]]
      assert(instantiated.isInstanceOf[TestClass2[Dep]])
      assert(instantiated.inner != null)
      assert(instantiated.inner eq context.get[Dep])
    }

    "support named bindings in zio.Has type parameters" in {
      import TypesCase1._

      val ctorA = ZIO.accessM { value: Has[Dep @Id("A")] => ZIO(TestClass2(value.get)) }
      val ctorB = ZIO.accessM { value: Has[Dep @Id("B")] => ZIO(TestClass2(value.get)) }

      val definition = PlannerInput.noGc(new ModuleDef {
        make[Dep].named("A").from[DepA]
        make[Dep].named("B").from[DepB]
        make[TestClass2[Dep]].named("A").fromHas(ctorA)
        make[TestClass2[Dep]].named("B").fromHas(ctorB)
      })

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = unsafeRun(injector.produceF[Task](plan).unsafeGet())

      val instantiated = context.get[TestClass2[Dep]]("A")
      assert(instantiated.inner.isInstanceOf[DepA])
      assert(!instantiated.inner.isInstanceOf[DepB])

      val instantiated1 = context.get[TestClass2[Dep]]("B")
      assert(instantiated1.inner.isInstanceOf[DepB])
      assert(!instantiated1.inner.isInstanceOf[DepA])
    }

    "handle multi-parameter Has with mixed args & env injection and a refinement return" in {
      import izumi.distage.fixtures.TraitCases.TraitCase2._

      def getDep1 = ZIO.access[Has[Dependency1]](_.get)
      def getDep2 = ZIO.access[Has[Dependency2]](_.get)

      val definition = PlannerInput.noGc(new ModuleDef {
        make[Dependency1]
        make[Dependency2]
        make[Dependency3]
        make[Trait3 { def dep1: Dependency1 }].fromHas((d3: Dependency3) => for {
          d1 <- getDep1
          d2 <- getDep2
        } yield new Trait3 {
          override val dep1 = d1
          override val dep2 = d2
          override val dep3 = d3
        })
      })

      val injector = mkNoCyclesInjector()
      val plan = injector.plan(definition)
      val context = unsafeRun(injector.produceF[Task](plan).unsafeGet())

      val instantiated = context.get[Trait3 { def dep1: Dependency1 }]

      assert(instantiated.dep1 eq context.get[Dependency1])
      assert(instantiated.dep2 eq context.get[Dependency2])
      assert(instantiated.dep3 eq context.get[Dependency3])
    }

    "can handle AnyVals" in {
      import izumi.distage.fixtures.TraitCases.TraitCase6._

      val definition = PlannerInput.noGc(new ModuleDef {
        make[Dep]
        make[AnyValDep]
        make[TestTrait].fromHas(ZIO.access[Has[AnyValDep]](h => new TestTrait {
          override val anyValDep: AnyValDep = h.get
        }))
      })

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = unsafeRun(injector.produceF[Task](plan).unsafeGet())

      assert(context.get[TestTrait].anyValDep != null)
      // AnyVal reboxing happened
      assert(context.get[TestTrait].anyValDep ne context.get[AnyValDep].asInstanceOf[AnyRef])
      assert(context.get[TestTrait].anyValDep.d eq context.get[Dep])
    }

  }

}
