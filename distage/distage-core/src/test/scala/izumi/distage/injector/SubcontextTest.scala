package izumi.distage.injector

import distage.{Activation, DIKey, Id, Injector, ModuleDef, PlanVerifier, Repo, TagK}
import izumi.distage.Subcontext
import izumi.distage.fixtures.ResourceCases.Suspend2
import izumi.distage.injector.SubcontextTest.*
import izumi.distage.model.PlannerInput
import izumi.distage.model.plan.Roots
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.platform.functional.Identity
import org.scalatest.exceptions.TestFailedException
import org.scalatest.wordspec.AnyWordSpec

class SubcontextTest extends AnyWordSpec with MkInjector {

  "support local contexts" in {
    val module = new ModuleDef {
      make[GlobalServiceDependency]
      make[GlobalService]

      // this will not be used/instantiated
      make[LocalService].from[LocalServiceBadImpl]

      makeSubcontext[Identity, Int]
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

    val definition = PlannerInput(module, Activation.empty, DIKey.get[Subcontext[Identity, Int]].named("test"))

    val injector = mkNoCyclesInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    val local = context.get[Subcontext[Identity, Int]]("test")
    assert(context.find[GlobalServiceDependency].nonEmpty)
    assert(context.find[GlobalService].nonEmpty)
    assert(context.find[LocalService].isEmpty)
    val out = local.provide[Arg]("x")(Arg(1)).produceRunSimple(identity)
    assert(out == 230)

    val result = PlanVerifier().verify[Identity](module, Roots.Everything, Injector.providedKeys(), Set.empty)
    assert(result.issues.isEmpty)
  }

  "support incomplete dsl chains (good case, no externals)" in {
    val module = new ModuleDef {
      make[GlobalServiceDependency]
      make[GlobalService]

      makeSubcontext[Identity, Int]
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

    val definition = PlannerInput(module, Activation.empty, DIKey.get[Subcontext[Identity, Int]].named("test"))

    val injector = mkNoCyclesInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    val local = context.get[Subcontext[Identity, Int]]("test")

    assert(local.produceRunSimple(identity) == 231)
  }

  "support self references" in {
    val module = new ModuleDef {
      makeSubcontext[Identity, Int](new ModuleDef {
        make[LocalRecursiveService].from[LocalRecursiveServiceGoodImpl]
        make[Int].from((summator: LocalRecursiveService) => summator.localSum)
      }).localDependency[Arg]
    }

    val definition = PlannerInput(module, Activation.empty, DIKey.get[Subcontext[Identity, Int]])

    val injector = mkNoCyclesInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    val local = context.get[Subcontext[Identity, Int]]

    assert(local.provide[Arg](Arg(10)).produceRunSimple(identity) == 20)
  }

  "support activations on subcontexts" in {
    val module = new ModuleDef {
      make[GlobalServiceDependency]
      make[GlobalService]
      make[LocalService].from[LocalServiceGoodImpl]
      make[Arg].fromValue(Arg(1))

      makeSubcontext[Identity, Int]
        .named("test")
        .tagged(Repo.Dummy)
        .extractWith {
          (summator: LocalService) =>
            summator.localSum
        }
        .localDependency[Boolean] // extraneous dependency is ignored

      makeSubcontext[Identity, Int]
        .named("test")
        .tagged(Repo.Prod)
        .extractWith {
          (summator: LocalService) =>
            summator.localSum - 2
        }
        .localDependency[Boolean]
    }

    val injector = mkNoCyclesInjector()
    val dummySubcontext = injector.produceGet[Subcontext[Identity, Int]]("test")(module, Activation(Repo.Dummy)).unsafeGet()
    val prodSubcontext = injector.produceGet[Subcontext[Identity, Int]]("test")(module, Activation(Repo.Prod)).unsafeGet()

    val dummyRes = dummySubcontext.produceRunSimple(identity)
    val prodRes = prodSubcontext.produceRun(x => x)

    assert(dummyRes == 230)
    assert(prodRes == 228)
  }

  "support activations in subcontexts" in {
    val module = new ModuleDef {
      make[GlobalServiceDependency]
      make[GlobalService]

      makeSubcontext[Identity, Int](new ModuleDef {
        make[LocalService].from[LocalServiceGoodImpl]

        make[Arg].tagged(Repo.Dummy).fromValue(Arg(1))
        make[Arg].tagged(Repo.Prod).fromValue(Arg(-1))
      }).extractWith {
        (_: LocalService).localSum
      }
    }

    val injector = mkNoCyclesInjector()
    val subcontext = injector.produceGet[Subcontext[Identity, Int]](module, Activation(Repo.Dummy)).unsafeGet()
    val prodSubcontext = injector.produceGet[Subcontext[Identity, Int]](module, Activation(Repo.Prod)).unsafeGet()

    val dummyRes = subcontext.produceRunSimple(identity)
    val prodRes = prodSubcontext.produceRunSimple(identity)

    assert(dummyRes == 230)
    assert(prodRes == 228)
  }

  "support value types in subcontexts" in {
    val module = new ModuleDef {
      make[GlobalServiceDependency]
      make[GlobalService]

      makeSubcontext[Identity, Int](new ModuleDef {
        make[LocalService].from[LocalServiceAnyValImpl]
        make[Int].from((_: LocalService).localSum)
      }).localDependency[Int]("arg")
    }

    val injector = mkNoCyclesInjector()
    val subcontext = injector.produceGet[Subcontext[Identity, Int]](module).unsafeGet()

    val resPlus1 = subcontext.provide[Int]("arg")(1).produceRun(identity)
    val resMinus1 = subcontext.provide[Int]("arg")(-1).produceRun(identity)

    assert(resPlus1 == 230)
    assert(resMinus1 == 228)
  }

  "usability: subcontext is pinned to its effect type, preventing confusion when Identity is used in `.use`" in {
    val module = new ModuleDef {
      make[GlobalServiceDependency]
      make[GlobalService]

      makeSubcontext[Suspend2[Throwable, _], Suspend2[Throwable, Int]](new ModuleDef {
        make[LocalService].from[LocalServiceGoodImpl]
      }).localDependency[Arg]
        .extractWith {
          (summator: LocalService) =>
            Suspend2(summator.localSum)
        }
    }

    def good[F[_]: QuasiIO: TagK](subcontext: Subcontext[F, F[Int]]): F[Int] = {
      subcontext.provide[Arg](Arg(1)).produce().use(effect => effect)
    }

    val injector = mkNoCyclesInjector()
    val subcontext = injector.produceGet[Subcontext[Suspend2[Throwable, _], Suspend2[Throwable, Int]]](module).unsafeGet()

    val res = good(subcontext)

    assert(res.run() == Right(230))

    val err = intercept[TestFailedException](assertCompiles("""
    def bad[F[_]](subcontext: Subcontext[F, F[Int]]): F[Int] = {
      subcontext.provide[Arg](Arg(1)).produce().use(effect => effect)
    }
    """))

    assert(err.getMessage.contains("implicit value") || err.getMessage.contains("implicit error"))
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
  class LocalServiceAnyValImpl(main: GlobalService, value: Int @Id("arg")) extends LocalService {
    def localSum: Int = main.sum(value) + 99
  }

  class LocalServiceBadImpl() extends LocalService {
    def localSum: Int = throw new RuntimeException("boom")
  }

  case class Arg(value: Int)

  trait LocalRecursiveService {
    def localSum: Int
  }

  class LocalRecursiveServiceGoodImpl(value: Arg, self: Subcontext[Identity, Int]) extends LocalRecursiveService {
    def localSum: Int = if (value.value > 0) {
      2 + self.provide[Arg](Arg(value.value - 1)).produceRunSimple(identity)
    } else {
      0
    }
  }

}
