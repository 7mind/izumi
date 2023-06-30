package izumi.distage.injector

import distage.{Activation, DIKey, Injector, ModuleDef, PlanVerifier}
import izumi.distage.LocalContext
import izumi.distage.injector.LocalContextTest.{LocalSummator, UselessDependency}
import izumi.distage.model.PlannerInput
import izumi.distage.model.plan.Roots
import izumi.fundamentals.platform.functional.Identity
import org.scalatest.wordspec.AnyWordSpec

class LocalContextTest extends AnyWordSpec with MkInjector {

  "support local contexts" in {
    val module = new ModuleDef {
      make[UselessDependency]
      make[LocalContextTest.Summator]

      make[LocalContext[Identity, Int]]
        .named("test")
        .fromModule(new ModuleDef {
          make[LocalSummator]
        })
        .external(DIKey.get[Int])
        .running {
          (summator: LocalContextTest.LocalSummator) =>
            summator.localSum
        }
    }

    val definition = PlannerInput(module, Activation.empty, DIKey.get[LocalContext[Identity, Int]].named("test"))

    val injector = mkNoCyclesInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    val local = context.get[LocalContext[Identity, Int]]("test")
    val out = local.add[Int](1).produceRun()
    assert(out == 230)

    val result = PlanVerifier().verify[Identity](module, Roots.Everything, Injector.providedKeys(), Set.empty)
    assert(result.issues.isEmpty)
  }

}

object LocalContextTest {
  class UselessDependency {
    def uselessConst: Int = 88
  }
  class Summator(uselessDependency: UselessDependency) {
    def sum(i: Int): Int = i + 42 + uselessDependency.uselessConst
  }

  class LocalSummator(main: Summator, value: Int) {
    def localSum: Int = main.sum(value) + 99
  }
}
