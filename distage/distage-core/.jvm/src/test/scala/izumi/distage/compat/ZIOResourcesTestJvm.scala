package izumi.distage.compat

import distage.{TagKK, *}
import izumi.distage.compat.ZIOResourcesTestJvm.*
import izumi.distage.model.definition.Binding.SingletonBinding
import izumi.distage.model.definition.{Activation, ImplDef, Lifecycle, ModuleDef}
import izumi.functional.bio.IO2
import izumi.fundamentals.platform.assertions.ScalatestGuards

import scala.annotation.unused
import izumi.distage.testkit.runner.spec.Assertion
import izumi.fundamentals.assertions.AssertionFailure
import izumi.distage.testkit.runner.spec.AnyWordSpec
import zio.*

object ZIOResourcesTestJvm {
  class Res { var allocated = false }
  class Res1 extends Res

  class DBConnection
  class MessageQueueConnection

  class MyApp(@unused db: DBConnection, @unused mq: MessageQueueConnection) {
    def run: Task[Unit] = ZIO.attempt(())
  }
}
final class ZIOResourcesTestJvm extends AnyWordSpec with ZIOTest with ScalatestGuards {

  "ZIO Scoped" should {

    "ZIO Scoped works" in {
      var l: List[Int] = Nil

      val dbResource = ZIO.acquireRelease(ZIO.succeed {
        l ::= 1
        new DBConnection
      })(_ => ZIO.succeed(l ::= 10))
      val mqResource = ZIO.acquireRelease(ZIO.succeed {
        l ::= 2
        new MessageQueueConnection
      })(_ => ZIO.succeed(l ::= 20))

      val module = new ModuleDef {
        make[DBConnection].fromResource(dbResource)
        make[MessageQueueConnection].fromResource(mqResource)
        make[MyApp]
      }

      unsafeRun(Injector[Task]().produceRun(module) {
        (myApp: MyApp) =>
          myApp.run
      })
      assert(l.reverse == List(1, 2, 20, 10))
    }

    "fromResource API should be compatible with provider and instance bindings of type Scoped ZIO" in {
      val resResource: ZIO[Scope, Throwable, Res1] = allocatedResource

      val definition: ModuleDef = new ModuleDef {
        make[Res]
          .named("instance").fromResource(resResource)

        make[Res].named("provider").fromResource {
          (_: Res @Id("instance")) =>
            resResource
        }
      }

      assertLifecycleBindings(definition)
    }

  }

  "ZLayer" should {

    "ZLayer works" in {
      var l: List[Int] = Nil

      val dbResource = ZLayer.scoped(ZIO.acquireRelease(ZIO.attempt {
        l ::= 1
        new DBConnection
      })(_ => ZIO.succeed(l ::= 10)))
      val mqResource = ZLayer.scoped(ZIO.acquireRelease(ZIO.attempt {
        l ::= 2
        new MessageQueueConnection
      })(_ => ZIO.succeed(l ::= 20)))

      val module = new ModuleDef {
        make[DBConnection].fromResource(dbResource)
        make[MessageQueueConnection].fromResource(mqResource)
        make[MyApp]
      }

      unsafeRun(Injector[Task]().produceRun(module) {
        (myApp: MyApp) =>
          myApp.run
      })
      assert(l.reverse == List(1, 2, 20, 10))
    }

    "fromResource API should be compatible with provider and instance bindings of type ZLayer" in {
      val resResource: ZLayer[Any, Throwable, Res1] = ZLayer.scoped(allocatedResource)

      val definition: ModuleDef = new ModuleDef {
        make[Res].named("instance").fromResource(resResource)

        make[Res].named("provider").fromResource {
          (_: Res @Id("instance")) =>
            resResource
        }
      }

      assertLifecycleBindings(definition)
    }

    "Conversions from ZLayer should fail to typecheck if the result type is unrelated to the binding type" in {
      brokenOnScala3 {
        // assertCompiles breaks on `make` macro
        assertCompiles("""
         new ModuleDef {
           make[String].fromResource { (_: Unit) => ZLayer.succeed("42") }
         }
        """)
      }
      val res = intercept[AssertionFailure](
        assertCompiles(
          """
         new ModuleDef {
           make[String].fromResource { (_: Unit) => ZLayer.succeed(42) }
         }
      """
        )
      )
      assert(res.getMessage.contains("implicit") || res.getMessage.contains("given instance"))
      assert(res.getMessage contains "AdaptFunctoid")
    }

  }

  "interruption" should {

    "Lifecycle.fromZIO(ZIO.forkScoped) is interruptible (https://github.com/7mind/izumi/issues/1138)" in {
      println("axiom: ZIO.forkScoped is interruptible")
      unsafeRun {
        for {
          latch <- Promise.make[Nothing, Unit]
          _ <- ZIO.scoped(scopedFiber(latch).flatMap(latch.await *> (_: Fiber[Nothing, Unit]).interrupt.unit))
        } yield ()
      }

      println("ZIO.forkScoped converted to Lifecycle is still interruptible")
      assertInterruptible(latch => Lifecycle.fromZIO(scopedFiber(latch)))

      println("ZManaged.fork converted to Lifecycle interrupts itself")
      unsafeRun {
        for {
          latch <- Promise.make[Nothing, Unit]
          doneFiber <- Lifecycle.fromZIO(scopedFiber(latch)).use(latch.await.as(_))
          exit <- doneFiber.await.timeoutFail("fiber was not interrupted")(60.seconds)
          _ = assert(exit.isInterrupted)
        } yield ()
      }

    }

    "In fa.flatMap(fb), fa and fb retain interruptibility" in {
      println("Lifecycle.fromZIO(_).flatMap is interruptible")
      assertInterruptible(latch => Lifecycle.fromZIO[Any](scopedFiber(latch)).flatMap(a => Lifecycle.unit[Task].map(_ => a)))

      println("_.flatMap(_ => Lifecycle.fromZIO(_)) is interruptible")
      assertInterruptible(latch => Lifecycle.unit[Task].flatMap(_ => Lifecycle.fromZIO[Any](scopedFiber(latch))))

    }

  }

  private def allocatedResource: ZIO[Scope, Throwable, Res1] = ZIO.acquireRelease(ZIO.attempt {
    val res = new Res1
    res.allocated = true
    res
  })(res => ZIO.succeed(res.allocated = false))

  private def scopedFiber(latch: Promise[Nothing, Unit]): ZIO[Scope, Nothing, Fiber[Nothing, Unit]] =
    (latch.succeed(()) *> ZIO.never)
      .onExit((_: Exit[Nothing, Unit]) => ZIO.succeed(println("ZIO interrupted")))
      .forkScoped

  private def assertInterruptible(allocate: Promise[Nothing, Unit] => Lifecycle[Task, Fiber[Nothing, Unit]]): Unit = unsafeRun {
    Promise.make[Nothing, Unit].flatMap(latch => allocate(latch).use(latch.await *> (_: Fiber[Nothing, Unit]).interrupt.unit))
  }

  private def assertLifecycleBindings(definition: ModuleDef): Unit = {
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

    assertLifecycleConversions(injector, plan)
  }

  private def assertLifecycleConversions(injector: Injector[Identity], plan: Plan): Unit = {
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

}
