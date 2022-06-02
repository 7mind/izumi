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
import izumi.fundamentals.platform.functional.Identity
import org.scalatest.GivenWhenThen
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
    val run: IO[Unit] = IO(println("Hello World!"))
  }
}

final class CatsResourcesTestJvm extends AnyWordSpec with GivenWhenThen with CatsIOPlatformDependentTest {

  private def createCPUPool(ioRuntime: => IORuntime): Lifecycle[Identity, ExecutionContext] = {
    new CatsIOPlatformDependentSupportModule {
      val res = createCPUPool(ioRuntime)
    }.res
  }

  "`No More Orphans` type provider is accessible" in {
    def y[R[_[_]]: izumi.fundamentals.orphans.`cats.effect.kernel.Sync`](): Unit = ()
    y()
  }

  "cats.Resource mdoc example works" in {
    val dbResource = Resource.make(IO { println("Connecting to DB!"); new DBConnection })(_ => IO(println("Disconnecting DB")))
    val mqResource = Resource.make(IO { println("Connecting to Message Queue!"); new MessageQueueConnection })(_ => IO(println("Disconnecting Message Queue")))

    val module = new ModuleDef {
      make[DBConnection].fromResource(dbResource)
      make[MessageQueueConnection].fromResource(mqResource)
      make[MyApp]
    }

    catsIOUnsafeRunSync {
      Injector[IO]()
        .produce(module, Roots.Everything).use {
          objects =>
            objects.get[MyApp].run
        }
    }
  }

  "cats.Resource mdoc example works with cyclic IORuntime (by-name case)" in {
    val dbResource = Resource.make(IO {
      println("Connecting to DB!"); new DBConnection
    })(_ => IO(println("Disconnecting DB")))
    val mqResource = Resource.make(IO {
      println("Connecting to Message Queue!"); new MessageQueueConnection
    })(_ => IO(println("Disconnecting Message Queue")))

    val module = new ModuleDef {
      make[DBConnection].fromResource(dbResource)
      make[MessageQueueConnection].fromResource(mqResource)
      make[MyApp]

      make[IORuntime].from {
        (cpuPool: ExecutionContext @Id("cpu"), blockingPool: ExecutionContext @Id("io"), scheduler: Scheduler, ioRuntimeConfig: IORuntimeConfig) =>
          IORuntime(cpuPool, blockingPool, scheduler, () => (), ioRuntimeConfig)
      }
      make[ExecutionContext].named("cpu").fromResource[CreateCPUPool]

      final class CreateCPUPool(ioRuntime: => IORuntime)
        extends Lifecycle.Of[Identity, ExecutionContext](
          createCPUPool(ioRuntime)
        )
    }

    catsIOUnsafeRunSync {
      Injector[IO]()
        .produce(module, Roots.Everything).use {
          objects =>
            assert(!objects.get[ExecutionContext]("cpu").isInstanceOf[DistageProxy])
            objects.get[MyApp].run
        }
    }
  }

  "cats.Resource mdoc example doesn't work with cyclic IORuntime (dynamic proxy case)" in {
    val dbResource = Resource.make(IO {
      println("Connecting to DB!"); new DBConnection
    })(_ => IO(println("Disconnecting DB")))
    val mqResource = Resource.make(IO {
      println("Connecting to Message Queue!"); new MessageQueueConnection
    })(_ => IO(println("Disconnecting Message Queue")))

    val module = new ModuleDef {
      make[DBConnection].fromResource(dbResource)
      make[MessageQueueConnection].fromResource(mqResource)
      make[MyApp]

      make[IORuntime].from {
        (cpuPool: ExecutionContext @Id("cpu"), blockingPool: ExecutionContext @Id("io"), scheduler: Scheduler, ioRuntimeConfig: IORuntimeConfig) =>
          IORuntime(cpuPool, blockingPool, scheduler, () => (), ioRuntimeConfig)
      }
      make[ExecutionContext].named("cpu").fromResource[CreateCPUPool]

      // DIFFERENCE: !!!
      final class CreateCPUPool(ioRuntime: IORuntime)
        extends Lifecycle.Of[Identity, ExecutionContext](
          createCPUPool(ioRuntime)
        )
    }

    catsIOUnsafeRunSync {
      Injector[IO]()
        .produce(module, Roots.Everything).use {
          objects =>
            assert(objects.get[ExecutionContext]("cpu").isInstanceOf[DistageProxy])
            objects.get[MyApp].run
        }
    }
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
        _: Res @Id("instance") =>
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

    val injector = Injector[Identity]()
    val plan = injector.plan(PlannerInput.everything(definition ++ new ModuleDef {
      addImplicit[Sync[IO]]
    }))

    def assert1(ctx: Locator) = {
      IO {
        val i1 = ctx.get[Res]("instance")
        val i2 = ctx.get[Res]("provider")
        assert(!(i1 eq i2))
        assert(i1.initialized && i2.initialized)
        Then("ok")
        i1 -> i2
      }
    }

    def assert2(i1: Res, i2: Res) = {
      IO(assert(!i1.initialized && !i2.initialized))
    }

    def produceSync[F[_]: TagK: Sync: DefaultModule] = Injector[F]().produce(plan)

    val ctxResource = produceSync[IO]

    catsIOUnsafeRunSync {
      ctxResource
        .use(assert1)
        .flatMap((assert2 _).tupled)
    }

    catsIOUnsafeRunSync {
      ctxResource
        .mapK(FunctionK.id[IO])
        .toCats
        .mapK(FunctionK.id[IO])
        .use(assert1)
        .flatMap((assert2 _).tupled)
    }
  }

  "cats instances for Lifecycle" in {
    def failImplicit[A](implicit a: A = null): A = a
    def request[F[_]: cats.effect.kernel.Sync] = {
      val F = cats.Functor[Lifecycle[F, _]]
      val M = cats.Monad[Lifecycle[F, _]]
      val m = cats.Monoid[Lifecycle[F, Int]]
      val _ = (F, m, M)
      val fail = failImplicit[cats.kernel.Order[Lifecycle[F, Int]]]
      assert(fail == null)
    }
    request[IO]
  }

  "Conversions from cats-effect Resource should fail to typecheck if the result type is unrelated to the binding type" in {
    assertCompiles(
      """
         new ModuleDef {
           make[String].fromResource { (_: Unit) => Resource.pure[cats.Id, String]("42") }
         }
      """
    )
    val res = intercept[TestFailedException](
      assertCompiles(
        """
         new ModuleDef {
           make[String].fromResource { (_: Unit) => Resource.pure[cats.Id, Int](42) }
         }
      """
      )
    )
    assert(res.getMessage contains "implicit")
    assert(res.getMessage contains "AdaptFunctoid.Aux")
  }

}
