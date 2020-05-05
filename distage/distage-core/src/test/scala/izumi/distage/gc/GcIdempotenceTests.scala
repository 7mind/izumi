package izumi.distage.gc

import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.{Activation, ModuleDef}
import distage.DIKey
import izumi.distage.model.plan.Roots
import org.scalatest.wordspec.AnyWordSpec

class GcIdempotenceTests extends AnyWordSpec with MkGcInjector {
  "Garbage-collecting injector" when {
    "plan is re-finished" should {
      "work with autosets" in {
        import GcCases.InjectorCase8._
        val injector = mkInjector()
        val plan = injector.plan(
          PlannerInput(
            new ModuleDef {
              many[Component]
                .add[TestComponent]

              make[App]
            },
            Activation.empty,
            Roots(DIKey.get[App]),
          )
        )

        val updated = injector.finish(plan.toSemi)
        val result = injector.produce(updated).unsafeGet()
        assert(updated.steps.size == plan.steps.size)

        assert(result.get[App].components.size == 1)
        assert(result.get[App].closeables.size == 1)
      }
    }

  }
}
