package izumi.distage.injector

import distage.ModuleDef
import izumi.distage.LocalContext
import izumi.distage.model.PlannerInput
import izumi.fundamentals.platform.functional.Identity
import org.scalatest.wordspec.AnyWordSpec

class LocalContextTest extends AnyWordSpec with MkInjector {

  "support local contexts" in {
    val definition = PlannerInput.everything(new ModuleDef {
      make[LocalContext[Identity, Int]].named("test").fromModule(new ModuleDef {}).running {
        (locval: Int) =>
          1 + locval
      }
    })

    val injector = mkNoCyclesInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    val local = context.get[LocalContext[Identity, Int]]("test")
    val out = local.add[Int](42).produceRun()
    assert(out == 1 + 42)
  }

}
