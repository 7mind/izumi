package izumi.distage.compat

import cats.arrow.FunctionK
import cats.effect.unsafe.{IORuntime, IORuntimeConfig, Scheduler}
import cats.effect.{IO, Resource, Sync}
import distage.*
import izumi.distage.compat.CatsResourcesTestJvm.*
import izumi.distage.model.definition.Binding.SingletonBinding
import izumi.distage.model.definition.{Id, ImplDef, Lifecycle, ModuleDef}
import izumi.distage.model.plan.Roots
import izumi.distage.model.provisioning.proxies.DistageProxy
import izumi.distage.modules.platform.CatsIOPlatformDependentSupportModule
import izumi.functional.bio.CatsToBIOConversions.*
import izumi.fundamentals.platform.assertions.ScalatestGuards
import izumi.fundamentals.platform.functional.Identity
import org.scalatest.exceptions.TestFailedException
import org.scalatest.wordspec.AnyWordSpec

import scala.annotation.unused
import scala.concurrent.ExecutionContext

object CatsResourcesTestJvm {
  class Res { var initialized: Boolean = false }
  class Res1 extends Res

  class DBConnection
  class MessageQueueConnection

  class MyApp(@unused db: DBConnection, @unused mq: MessageQueueConnection, @unused r: IORuntime) {
    val run: IO[Boolean] = IO.pure(true)
  }
}

final class CatsResourcesTestJvm extends AnyWordSpec with CatsIOPlatformDependentTest with ScalatestGuards {

  "`No More Orphans` type provider is accessible" in {
    def y[R[_[_]]: izumi.fundamentals.orphans.`cats.effect.kernel.Sync`](): Unit = ()
    y()
  }

  // [M5-D01] Disabled — izumi-reflect 3.0.8/3.0.9 does not η-normalise `Bifunctorized[IO, _, _]` against
  // `Bifunctorized[λ x => IO[x], _, _]`. Binding-side stores raw IO (captured via `F[_]: TagK` in
  // `LifecycleAdapters.providerFromCatsProvider`); Injector-side stores η-expanded IO. `LightTypeTag.<:<`
  // rejects the equivalence, so `EffectStrategyDefaultImpl` raises IncompatibleEffectType.
  // See defects.md [M5-D01] for the full investigation. Fix has to land in izumi-reflect.
  "cats.Resource mdoc example works" ignore {
    val dbResource = Resource.make(IO(new DBConnection))(_ => IO.unit)
    val mqResource = Resource.make(IO(new MessageQueueConnection))(_ => IO.unit)

    val module = new ModuleDef {
      make[DBConnection].fromResource(dbResource)
      make[MessageQueueConnection].fromResource(mqResource)
      make[MyApp]
    }

    val res = catsIOUnsafeRunSync {
      Injector[izumi.functional.bio.Bifunctorized[IO, +_, +_]]()
        .produce(module, Roots.Everything).use {
          objects =>
            objects.get[MyApp].run
        }
    }
    assert(res)
  }

  // [M5-D01] Disabled — see defects.md [M5-D01].
  "cats.Resource mdoc example works with cyclic IORuntime (by-name case)" ignore {
    val dbResource = Resource.make(IO(new DBConnection))(_ => IO.unit)
    val mqResource = Resource.make(IO(new MessageQueueConnection))(_ => IO.unit)

    val module = new ModuleDef {
      make[DBConnection].fromResource(dbResource)
      make[MessageQueueConnection].fromResource(mqResource)
      make[MyApp]

      make[IORuntime].from {
        (cpuPool: ExecutionContext @Id("cpu"), blockingPool: ExecutionContext @Id("io"), scheduler: Scheduler, ioRuntimeConfig: IORuntimeConfig) =>
          IORuntime(cpuPool, blockingPool, scheduler, () => (), ioRuntimeConfig)
      }
      make[ExecutionContext].named("cpu").fromResource[izumi.functional.bio.Bifunctorized.IdentityBifunctorized, Throwable, CreateCPUPool](distage.ClassConstructor[CreateCPUPool])

      final class CreateCPUPool(@unused ioRuntime: => IORuntime)
        extends Lifecycle.Of[izumi.functional.bio.Bifunctorized.IdentityBifunctorized, Throwable, ExecutionContext](
          CatsIOPlatformDependentSupportModule.createCPUPool
        )
    }

    val res = catsIOUnsafeRunSync {
      Injector[izumi.functional.bio.Bifunctorized[IO, +_, +_]]()
        .produce(module, Roots.Everything).use {
          objects =>
            assert(!objects.get[ExecutionContext]("cpu").isInstanceOf[DistageProxy])
            objects.get[MyApp].run
        }
    }
    assert(res)
  }

  // [M5-D01] Disabled — see defects.md [M5-D01].
  "cats.Resource mdoc example doesn't work with cyclic IORuntime (dynamic proxy case)" ignore {
    val dbResource = Resource.make(IO(new DBConnection))(_ => IO.unit)
    val mqResource = Resource.make(IO(new MessageQueueConnection))(_ => IO.unit)

    val module = new ModuleDef {
      make[DBConnection].fromResource(dbResource)
      make[MessageQueueConnection].fromResource(mqResource)
      make[MyApp]

      make[IORuntime].from {
        (cpuPool: ExecutionContext @Id("cpu"), blockingPool: ExecutionContext @Id("io"), scheduler: Scheduler, ioRuntimeConfig: IORuntimeConfig) =>
          IORuntime(cpuPool, blockingPool, scheduler, () => (), ioRuntimeConfig)
      }
      make[ExecutionContext].named("cpu").fromResource[izumi.functional.bio.Bifunctorized.IdentityBifunctorized, Throwable, CreateCPUPool](distage.ClassConstructor[CreateCPUPool])

      // DIFFERENCE: not by-name
      final class CreateCPUPool(@unused ioRuntime: IORuntime)
        extends Lifecycle.Of[izumi.functional.bio.Bifunctorized.IdentityBifunctorized, Throwable, ExecutionContext](
          CatsIOPlatformDependentSupportModule.createCPUPool
        )
    }

    val res = catsIOUnsafeRunSync {
      Injector[izumi.functional.bio.Bifunctorized[IO, +_, +_]]()
        .produce(module, Roots.Everything).use {
          objects =>
            assert(objects.get[ExecutionContext]("cpu").isInstanceOf[DistageProxy])
            objects.get[MyApp].run
        }
    }
    assert(res)
  }

  "Lifecycle API should be compatible with provider and instance bindings of type cats.effect.Resource" in {
    val resResource: Resource[IO, Res1] = Resource.make(
      acquire = IO {
        val res = new Res1; res.initialized = true; res
      }
    )(release = res => IO(res.initialized = false))

    val definition: ModuleDef = new ModuleDef {
      make[Res].named("instance").fromResource(resResource)

      make[Res].named("provider").fromResource {
        (_: Res @Id("instance")) =>
          resResource
      }
    }

    definition.bindings.foreach {
      case SingletonBinding(_, implDef @ ImplDef.ResourceImpl(_, _, ImplDef.ProviderImpl(providerImplType, fn)), _, _, _) =>
        assert(implDef.implType == SafeType.get[Res1])
        assert(providerImplType == SafeType.get[Lifecycle.FromCats[IO, Res1]])
        assert(fn.diKeys contains DIKey.get[Sync[IO]])
      case _ =>
        fail()
    }

    val injector = Injector[izumi.functional.bio.Bifunctorized.IdentityBifunctorized]()
    val plan = injector.planUnsafe(PlannerInput.everything(definition ++ new ModuleDef {
      addImplicit[Sync[IO]]
    }))

    def assert1(ctx: Locator) = {
      IO {
        val i1 = ctx.get[Res]("instance")
        val i2 = ctx.get[Res]("provider")
        assert(!(i1 eq i2))
        assert(i1.initialized && i2.initialized)
        i1 -> i2
      }
    }

    def assert2(i1: Res, i2: Res) = {
      IO(assert(!i1.initialized && !i2.initialized))
    }

    def produceSync[F[_]: TagK: cats.effect.kernel.Async](implicit dm: DefaultModule[izumi.functional.bio.Bifunctorized[F, +_, +_]]) =
      Injector[izumi.functional.bio.Bifunctorized[F, +_, +_]]().produce(plan)

    val ctxResource = produceSync[IO]

    catsIOUnsafeRunSync {
      ctxResource
        .use(assert1)
        .flatMap((assert2 _).tupled)
    }

    catsIOUnsafeRunSync {
      ctxResource
        .mapK(izumi.functional.bio.data.Morphism2.identity[izumi.functional.bio.Bifunctorized[IO, +_, +_]])
        .toCats
        .mapK(FunctionK.id[IO])
        .use(assert1)
        .flatMap((assert2 _).tupled)
    }
  }

  "BIO instances for Lifecycle" in {
    def failImplicit[A](implicit a: A = null): A = a
    def request[F[+_, +_]: izumi.functional.bio.IO2: izumi.functional.bio.Primitives2] = {
      val F = izumi.functional.bio.Functor2[Lifecycle[F, +_, +_]]
      val M = izumi.functional.bio.Monad2[Lifecycle[F, +_, +_]]
      val _ = (F, M)
      val fail = failImplicit[cats.kernel.Order[Lifecycle[F, Throwable, Int]]]
      assert(fail == null)
    }
    request[izumi.functional.bio.Bifunctorized[IO, +_, +_]]
  }

  "Conversions from cats-effect Resource should fail to typecheck if the result type is unrelated to the binding type" in {
    brokenOnScala3 {
      // assertCompiles breaks on `make` macro
      assertCompiles(
        """
         new ModuleDef {
           make[String].fromResource { (_: Unit) => Resource.pure[cats.Id, String]("42") }
         }
      """
      )
    }
    val res = intercept[TestFailedException](
      assertCompiles(
        """
         new ModuleDef {
           make[String].fromResource { (_: Unit) => Resource.pure[cats.Id, Int](42) }
         }
      """
      )
    )
    // Scala 3.7 emits a tasty-reflect "MUST enable -Yretain-trees" message instead of a clean implicit-search failure for this overload-resolution case.
    assert(
      (res.getMessage contains "implicit") || (res.getMessage contains "No given instance") || (res.getMessage contains "-Yretain-trees")
    )
    // Only require AdaptFunctoid mention if Scala 3 produced an implicit-search error (Scala 3.7 retain-trees branch doesn't mention it).
    if (!(res.getMessage contains "-Yretain-trees")) {
      assert(res.getMessage contains "AdaptFunctoid")
    }
  }

}
