package izumi.distage.gc

import distage.DIKey
import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.exceptions.UnsupportedOpException
import izumi.distage.model.plan.GCMode
import org.scalatest.wordspec.AnyWordSpec

class GcBasicTests extends AnyWordSpec with MkGcInjector {
  "Garbage-collecting injector" should {

    "fail during planning on non-by-name loops involving only final classes" in {
      import GcCases.InjectorCase10._

      val injector = mkInjector()
      intercept[UnsupportedOpException] {
        injector.plan(PlannerInput(new ModuleDef {
          make[Circular1]
          make[Circular2]
        }, GCMode(DIKey.get[Circular2])))
      }
    }

    "handle by-name circular dependencies with sets through refs/2" in {
      import GcCases.InjectorCase13._

      val injector = mkNoCglibInjector()
      val plan = injector.plan(PlannerInput(new ModuleDef {
        make[Circular1]
        make[Circular2]
        make[T1]
        make[Box[T1]].from(new Box(new T1))
      }, GCMode(DIKey.get[Circular1], DIKey.get[Circular2])))

      val result = injector.produceUnsafe(plan)

      assert(result.get[Circular1] != null)
      assert(result.get[Circular2] != null)
      assert(result.get[Circular1].q == result.get[Circular2].q)
    }

  }
}
