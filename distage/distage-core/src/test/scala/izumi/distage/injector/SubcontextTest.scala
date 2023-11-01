package izumi.distage.injector

import distage.{Activation, DIKey, Injector, ModuleDef, PlanVerifier, Repo}
import izumi.distage.Subcontext
import izumi.distage.injector.SubcontextTest.*
import izumi.distage.model.PlannerInput
import izumi.distage.model.plan.Roots
import izumi.fundamentals.platform.functional.Identity
import org.scalatest.wordspec.AnyWordSpec

class SubcontextTest extends AnyWordSpec with MkInjector {

  "support local contexts" in {
    val module = new ModuleDef {
      make[GlobalServiceDependency]
      make[GlobalService]

      // this will not be used/instantiated
      make[LocalService].from[LocalServiceBadImpl]

      makeSubcontext[Int]
        .named("test")
        .withSubmodule {
          new ModuleDef {
            make[LocalService]
              .from[LocalServiceGoodImpl]
              .annotateParameter[Arg]("x")
          }
        }
        .extractWith {
          (summator: LocalService) =>
            summator.localSum
        }
        .localDependency[Arg]("x")
    }

    val definition = PlannerInput(module, Activation.empty, DIKey.get[Subcontext[Int]].named("test"))

    val injector = mkNoCyclesInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    val local = context.get[Subcontext[Int]]("test")
    assert(context.find[GlobalServiceDependency].nonEmpty)
    assert(context.find[GlobalService].nonEmpty)
    assert(context.find[LocalService].isEmpty)
    val out = local.provide[Arg]("x")(Arg(1)).produceRun(identity)
    assert(out == 230)

    val result = PlanVerifier().verify[Identity](module, Roots.Everything, Injector.providedKeys(), Set.empty)
    assert(result.issues.isEmpty)
  }

  "support incomplete dsl chains (good case, no externals)" in {
    val module = new ModuleDef {
      make[GlobalServiceDependency]
      make[GlobalService]

      makeSubcontext[Int]
        .named("test")
        .withSubmodule(new ModuleDef {
          make[Arg].fromValue(Arg(2))
          make[LocalService].from[LocalServiceGoodImpl]
        })
        .extractWith {
          (summator: LocalService) =>
            summator.localSum
        }
    }

    val definition = PlannerInput(module, Activation.empty, DIKey.get[Subcontext[Int]].named("test"))

    val injector = mkNoCyclesInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    val local = context.get[Subcontext[Int]]("test")

    assert(local.produceRun(identity) == 231)
  }

  "support self references" in {
    val module = new ModuleDef {
      makeSubcontext[Int](new ModuleDef {
        make[LocalRecursiveService].from[LocalRecursiveServiceGoodImpl]
      })
        .extractWith {
          (summator: LocalRecursiveService) =>
            summator.localSum
        }
        .localDependency[Arg]
    }

    val definition = PlannerInput(module, Activation.empty, DIKey.get[Subcontext[Int]])

    val injector = mkNoCyclesInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    val local = context.get[Subcontext[Int]]

    assert(local.provide(Arg(10)).produceRun(identity) == 20)
  }

  "support activations on subcontexts" in {
    val module = new ModuleDef {
      make[GlobalServiceDependency]
      make[GlobalService]
      make[LocalService].from[LocalServiceGoodImpl]
      make[Arg].fromValue(Arg(1))

      makeSubcontext[Int]
        .named("test")
        .tagged(Repo.Dummy)
        .extractWith {
          (summator: LocalService) =>
            summator.localSum
        }
        .localDependency[Int]

      makeSubcontext[Int]
        .named("test")
        .tagged(Repo.Prod)
        .extractWith {
          (summator: LocalService) =>
            summator.localSum - 2
        }
        .localDependency[Int]
    }

    val injector = mkNoCyclesInjector()
    val dummySubcontext = injector.produceGet[Subcontext[Int]]("test")(module, Activation(Repo.Dummy)).unsafeGet()
    val prodSubcontext = injector.produceGet[Subcontext[Int]]("test")(module, Activation(Repo.Prod)).unsafeGet()

    val dummyRes = dummySubcontext.produceRun(identity)
    val prodRes = prodSubcontext.produceRun(x => x: Identity[Int])

    assert(dummyRes == 230)
    assert(prodRes == 228)
  }

  "support activations in subcontexts" in {
    val module = new ModuleDef {
      make[GlobalServiceDependency]
      make[GlobalService]

      makeSubcontext[Int](new ModuleDef {
        make[LocalService].from[LocalServiceGoodImpl]

        make[Arg].tagged(Repo.Dummy).fromValue(Arg(1))
        make[Arg].tagged(Repo.Prod).fromValue(Arg(-1))
      })
        .extractWith {
          (summator: LocalService) =>
            summator.localSum
        }
    }

    val injector = mkNoCyclesInjector()
    val subcontext = injector.produceGet[Subcontext[Int]](module, Activation(Repo.Dummy)).unsafeGet()
    val prodSubcontext = injector.produceGet[Subcontext[Int]](module, Activation(Repo.Prod)).unsafeGet()

    val dummyRes = subcontext.produceRun(identity)
    val prodRes = prodSubcontext.produceRun(identity)

    assert(dummyRes == 230)
    assert(prodRes == 228)
  }
}

object SubcontextTest {
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

  class LocalRecursiveServiceGoodImpl(value: Arg, self: Subcontext[Int]) extends LocalRecursiveService {
    def localSum: Int = if (value.value > 0) {
      2 + self.provide(Arg(value.value - 1)).produceRun(identity)
    } else {
      0
    }
  }

}
