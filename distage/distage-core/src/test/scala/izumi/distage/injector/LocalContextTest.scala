package izumi.distage.injector

import distage.ModuleDef
import izumi.distage.LocalContext
import izumi.distage.injector.LocalContextTest.LocalSummator
import izumi.distage.model.PlannerInput
import izumi.fundamentals.platform.functional.Identity
import org.scalatest.wordspec.AnyWordSpec

class LocalContextTest extends AnyWordSpec with MkInjector {

  "support local contexts" in {
    val definition = PlannerInput.everything(new ModuleDef {
      make[LocalContextTest.Summator]
      make[LocalContext[Identity, Int]]
        .named("test")
        .fromModule(new ModuleDef {
          make[LocalSummator]
        })
        .running {
          (summator: LocalContextTest.LocalSummator) =>
            summator.localSum
        }
    })

    val injector = mkNoCyclesInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    val local = context.get[LocalContext[Identity, Int]]("test")
    val out = local.add[Int](1).produceRun()
    assert(out == 142)
  }

}

object LocalContextTest {
  class Summator {
    def sum(i: Int): Int = i + 42
  }

  class LocalSummator(main: Summator, value: Int) {
    def localSum: Int = main.sum(value) + 99
  }
}
