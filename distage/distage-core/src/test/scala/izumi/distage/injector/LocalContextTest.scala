package izumi.distage.injector

import distage.ModuleDef
import izumi.distage.LocalContext
import izumi.distage.model.PlannerInput
import izumi.fundamentals.platform.functional.Identity
import org.scalatest.wordspec.AnyWordSpec

class LocalContextTest extends AnyWordSpec with MkInjector {

  "support local contexts" in {

    val definition = PlannerInput.everything(new ModuleDef {
      make[LocalContext[Identity, Unit]].named("test").fromModule(new ModuleDef {}).running {
        () =>
          println("hi")
      }
    })

    val injector = mkNoCyclesInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    println(context)
  }

}
