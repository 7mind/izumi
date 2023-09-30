package izumi.distage.injector

import distage.{Activation, DIKey, Injector, Module, ModuleDef, PlanVerifier}
import izumi.distage.LocalContext
import izumi.distage.injector.LocalContextTest.*
import izumi.distage.model.PlannerInput
import izumi.distage.model.plan.Roots
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks
import org.scalatest.wordspec.AnyWordSpec

class LocalContextTest extends AnyWordSpec with MkInjector {

  "support local contexts" in {
    val module = new ModuleDef {
      make[GlobalServiceDependency]
      make[GlobalService]

      // this will not be used/instantiated
      make[LocalService].from[LocalServiceBadImpl]

      make[LocalContext[Identity, Int]]
        .named("test")
        .fromLocalContext(new ModuleDef {
          make[LocalService].from[LocalServiceGoodImpl]
        }.running {
          (summator: LocalService) =>
            summator.localSum
        })
        .external(DIKey.get[Arg])
    }

    val definition = PlannerInput(module, Activation.empty, DIKey.get[LocalContext[Identity, Int]].named("test"))

    val injector = mkNoCyclesInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    val local = context.get[LocalContext[Identity, Int]]("test")
    assert(context.find[GlobalServiceDependency].nonEmpty)
    assert(context.find[GlobalService].nonEmpty)
    assert(context.find[LocalService].isEmpty)
    val out = local.provide[Arg](Arg(1)).produceRun()
    assert(out == 230)

    val result = PlanVerifier().verify[Identity](module, Roots.Everything, Injector.providedKeys(), Set.empty)
    assert(result.issues.isEmpty)
  }

  "support incomplete dsl chains (good case, no externals)" in {
    val module = new ModuleDef {
      make[GlobalServiceDependency]
      make[GlobalService]

      make[LocalContext[Identity, Int]]
        .named("test")
        .fromLocalContext(
          new ModuleDef {
            make[Arg].fromValue(Arg(2))
            make[LocalService].from[LocalServiceGoodImpl]
          }.running {
            (summator: LocalService) =>
              summator.localSum
          }
        )
    }

    val definition = PlannerInput(module, Activation.empty, DIKey.get[LocalContext[Identity, Int]].named("test"))

    val injector = mkNoCyclesInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    val local = context.get[LocalContext[Identity, Int]]("test")

    assert(local.produceRun() == 231)
  }

  "support self references" in {
    val module = new ModuleDef {
      make[LocalContext[Identity, Int]]
        .fromLocalContext(
          new ModuleDef {
            make[LocalRecursiveService].from[LocalRecursiveServiceGoodImpl]
          }.running {
            (summator: LocalRecursiveService) =>
              summator.localSum
          }
        ).external[Arg]
    }

    val definition = PlannerInput(module, Activation.empty, DIKey.get[LocalContext[Identity, Int]])

    val injector = mkNoCyclesInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    val local = context.get[LocalContext[Identity, Int]]

    assert(local.provide(Arg(10)).produceRun() == 20)
  }

  "support various local context syntax modes" in {
    val module1 = new ModuleDef {
      make[LocalContext[Identity, Int]]
        .named("test")
        .fromLocalContext(Module.empty.running {
          (summator: LocalService) =>
            summator.localSum
        })
        .external(DIKey.get[Int])
    }

    val module2 = new ModuleDef {
      make[LocalContext[Identity, Int]]
        .named("test")
        .fromLocalContext(Module.empty.running {
          (summator: LocalService) =>
            summator.localSum
        })
    }

    val module3 = new ModuleDef {
      make[LocalContext[cats.effect.IO, Int]]
        .named("test")
        .fromLocalContext(Module.empty.running {
          (summator: LocalService) =>
            cats.effect.IO(summator.localSum)
        })
    }

    Quirks.discard((module1, module2, module3))
  }
}

object LocalContextTest {
  class GlobalServiceDependency {
    def uselessConst: Int = 88
  }
  class GlobalService(uselessDependency: GlobalServiceDependency) {
    def sum(i: Int): Int = i + 42 + uselessDependency.uselessConst
  }

  trait LocalService {
    def localSum: Int
  }
  class LocalServiceGoodImpl(main: GlobalService, value: Arg) extends LocalService {
    def localSum: Int = main.sum(value.value) + 99
  }

  class LocalServiceBadImpl() extends LocalService {
    def localSum: Int = throw new RuntimeException("boom")
  }

  case class Arg(value: Int)

  trait LocalRecursiveService {
    def localSum: Int
  }

  class LocalRecursiveServiceGoodImpl(value: Arg, self: LocalContext[Identity, Int]) extends LocalRecursiveService {
    def localSum: Int = if (value.value > 0) {
      2 + self.provide(Arg(value.value - 1)).produceRun()
    } else {
      0
    }
  }

}
