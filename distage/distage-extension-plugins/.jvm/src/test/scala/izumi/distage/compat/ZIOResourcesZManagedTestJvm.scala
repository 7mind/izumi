package izumi.distage.compat

import cats.arrow.FunctionK
import distage.{TagKK, *}
import izumi.distage.compat.ZIOResourcesZManagedTestJvm.*
import izumi.distage.model.definition.Binding.SingletonBinding
import izumi.distage.model.definition.ImplDef
import izumi.functional.bio.IO2
import izumi.fundamentals.platform.assertions.ScalatestGuards

import scala.annotation.unused
import izumi.distage.testkit.runner.spec.Assertion
import izumi.fundamentals.assertions.AssertionFailure
import izumi.distage.testkit.runner.spec.AnyWordSpec
import zio.*
import zio.managed.ZManaged

object ZIOResourcesZManagedTestJvm {
  class Res { var allocated = false }
  class Res1 extends Res

  class DBConnection
  class MessageQueueConnection

  class MyApp(@unused db: DBConnection, @unused mq: MessageQueueConnection) {
    def run: Task[Unit] = ZIO.attempt(())
  }
}
final class ZIOResourcesZManagedTestJvm extends AnyWordSpec with ScalatestGuards {

  protected def unsafeRun[E, A](eff: => ZIO[Any, E, A]): A = Unsafe.unsafe(implicit unsafe => zio.Runtime.default.unsafe.run(eff).getOrThrowFiberFailure())

  "ZManaged" should {

    "ZManaged works" in {
      var l: List[Int] = Nil

      val dbResource = ZManaged.acquireReleaseWith(ZIO.succeed {
        l ::= 1
        new DBConnection
      })(_ => ZIO.succeed(l ::= 2))
      val mqResource = ZManaged.acquireReleaseWith(ZIO.succeed {
        l ::= 3
        new MessageQueueConnection
      })(_ => ZIO.succeed(l ::= 4))

      val module = new ModuleDef {
        make[DBConnection].fromResource(dbResource)
        make[MessageQueueConnection].fromResource(mqResource)
        make[MyApp]
      }

      unsafeRun(Injector[Task]().produceRun(module) {
        (myApp: MyApp) =>
          myApp.run
      })
      assert(l == List(2, 4, 3, 1))
    }

    "fromResource API should be compatible with provider and instance bindings of type ZManaged" in {
      val resResource: ZManaged[Any, Throwable, Res1] = ZManaged.acquireReleaseWith(
        acquire = ZIO.attempt {
          val res = new Res1; res.allocated = true; res
        }
      )(release = res => ZIO.succeed(res.allocated = false))

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
          assert(providerImplType == SafeType.get[Lifecycle.FromZIO[Any, Throwable, Res1]])
          assert(!fn.diKeys.exists(_.toString.contains("cats.effect")))
        case _ =>
          fail()
      }

      val injector = Injector()
      val plan = injector.planUnsafe(PlannerInput.everything(definition, Activation.empty))

      def assertAcquired(ctx: Locator): Task[(Res, Res)] = {
        ZIO.attempt {
          val i1 = ctx.get[Res]("instance")
          val i2 = ctx.get[Res]("provider")
          assert(i1 ne i2)
          assert((i1.allocated -> i2.allocated) == (true -> true))
          i1 -> i2
        }
      }

      def assertReleased(i1: Res, i2: Res): Task[Assertion] = {
        ZIO.attempt(assert((i1.allocated -> i2.allocated) == (false -> false)))
      }

      def produceBIO[F[+_, +_]: TagKK: IO2]: Lifecycle[F[Throwable, _], Locator] = injector.produceCustomF[F[Throwable, _]](plan)

      val ctxResource: Lifecycle[Task, Locator] = produceBIO[IO]

      // works normally
      unsafeRun {
        ctxResource
          .use(assertAcquired)
          .flatMap((assertReleased _).tupled)
      }

      // works when Lifecycle is converted to cats.Resource
      unsafeRun {
        import izumi.functional.bio.catz.BIOToMonadCancel
        ctxResource.toCats
          .use(assertAcquired)
          .flatMap((assertReleased _).tupled)
      }

      // works when Lifecycle is converted to scoped zio.ZIO
      unsafeRun {
        ZIO
          .scoped {
            ctxResource.toZIO
              .flatMap(assertAcquired)
          }
          .flatMap((assertReleased _).tupled)
      }
    }

    "Conversions from ZManaged should fail to typecheck if the result type is unrelated to the binding type" in {
      brokenOnScala3 {
        // assertCompiles breaks on `make` macro
        assertCompiles("""
         new ModuleDef {
           make[String].fromResource { (_: Unit) => ZManaged.succeed("42") }
         }
      """)
      }
      val res = intercept[AssertionFailure](
        assertCompiles(
          """
         new ModuleDef {
           make[String].fromResource { (_: Unit) => ZManaged.succeed(42) }
         }
      """
        )
      )
      assert(res.getMessage.contains("implicit") || res.getMessage.contains("given instance"))
      assert(res.getMessage contains "AdaptFunctoid")
    }

  }

  "interruption" should {

    "Lifecycle.fromZManaged(ZManaged.fork) is interruptible (https://github.com/7mind/izumi/issues/1138)" in {
      println("axiom: ZManaged.fork is interruptible")
      unsafeRun {
        Promise.make[Nothing, Unit].flatMap { latch =>
          managedFiber(latch, "ZManaged interrupted").use(latch.await *> (_: Fiber[Nothing, Unit]).interrupt.unit)
        }
      }

      println("ZManaged.fork converted to Lifecycle is still interruptible")
      assertInterruptible(latch => Lifecycle.fromZManaged(managedFiber(latch, "Lifecycle interrupted")))

      println("ZManaged.fork converted to Lifecycle interrupts itself")
      unsafeRun {
        for {
          latch <- Promise.make[Nothing, Unit]
          doneFiber <- Lifecycle.fromZManaged(managedFiber(latch, "Lifecycle interrupted")).use(latch.await.as(_))
          exit <- doneFiber.await.timeoutFail("fiber was not interrupted")(60.seconds)
          _ = assert(exit.isInterrupted)
        } yield ()
      }

      println("Even `ZManaged -> Resource -> Lifecycle` chain is still interruptible")
      assertInterruptible { latch =>
        import zio.interop.catz.*
        Lifecycle.fromCats[Task, Fiber[Nothing, Unit]](
          managedFiber(latch, "Resource interrupted").toResourceZIO.mapK(FunctionK.id[Task].widen[ZIO[Any, Throwable, _]])
        )
      }

      println("Even `Scoped ZIO -> ZManaged -> Resource -> Lifecycle` chain is still interruptible")
      assertInterruptible { latch =>
        import zio.interop.catz.*
        Lifecycle.fromCats[Task, Fiber[Nothing, Unit]](
          ZManaged.scoped {
            (latch.succeed(()) *> ZIO.never)
              .onExit((_: Exit[Throwable, Unit]) => ZIO.succeed(println("Resource interrupted")))
              .forkScoped
          }.toResourceZIO.mapK(FunctionK.id[Task].widen[ZIO[Any, Throwable, _]])
        )
      }

    }

    "In fa.flatMap(fb), fa and fb retain interruptibility" in {
      println("Lifecycle.fromZIO(_).flatMap is interruptible")
      assertInterruptible { latch =>
        Lifecycle.fromZManaged[Any, Throwable, Fiber[Nothing, Unit]](managedFiber(latch, "ZManaged interrupted"))
          .flatMap(a => Lifecycle.unit[Task].map(_ => a))
      }

      println("_.flatMap(_ => Lifecycle.fromZIO(_)) is interruptible")
      assertInterruptible { latch =>
        Lifecycle.unit[Task].flatMap(_ => Lifecycle.fromZManaged[Any, Throwable, Fiber[Nothing, Unit]](managedFiber(latch, "ZManaged interrupted")))
      }

    }

  }

  private def managedFiber(latch: Promise[Nothing, Unit], message: String): ZManaged[Any, Nothing, Fiber[Nothing, Unit]] =
    ZManaged.fromZIO(latch.succeed(()) *> ZIO.never)
      .onExit((_: Exit[Nothing, Unit]) => ZIO.succeed(println(message)))
      .fork

  private def assertInterruptible(allocate: Promise[Nothing, Unit] => Lifecycle[Task, Fiber[Nothing, Unit]]): Unit = unsafeRun {
    Promise.make[Nothing, Unit].flatMap(latch => allocate(latch).use(latch.await *> (_: Fiber[Nothing, Unit]).interrupt.unit))
  }

}
