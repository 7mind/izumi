package izumi.distage.compat

import cats.arrow.FunctionK
import distage.{TagKK, *}
import izumi.distage.compat.ZIOResourcesTestJvm.*
import izumi.distage.model.definition.Binding.SingletonBinding
import izumi.distage.model.definition.{Activation, ImplDef, Lifecycle, ModuleDef}
import izumi.functional.bio.IO2

import scala.annotation.unused
import org.scalatest.GivenWhenThen
import org.scalatest.exceptions.TestFailedException
import org.scalatest.wordspec.AnyWordSpec
import zio.*
import zio.managed.ZManaged

object ZIOResourcesTestJvm {
  class Res { var initialized = false }
  class Res1 extends Res

  class DBConnection
  class MessageQueueConnection

  class MyApp(@unused db: DBConnection, @unused mq: MessageQueueConnection) {
    val run = ZIO.attempt(println("Hello World!"))
  }
}
final class ZIOResourcesTestJvm extends AnyWordSpec with GivenWhenThen with ZIOTest {

  "ZManaged" should {
    "ZManaged works" in {
      val dbResource = ZManaged.acquireReleaseWith(ZIO.succeed {
        println("Connecting to DB!")
        new DBConnection
      })(_ => ZIO.succeed(println("Disconnecting DB")))
      val mqResource = ZManaged.acquireReleaseWith(ZIO.succeed {
        println("Connecting to Message Queue!")
        new MessageQueueConnection
      })(_ => ZIO.succeed(println("Disconnecting Message Queue")))

      val module = new ModuleDef {
        make[DBConnection].fromResource(dbResource)
        make[MessageQueueConnection].fromResource(mqResource)
        make[MyApp]
      }

      unsafeRun(Injector[Task]().produceRun(module) {
        (myApp: MyApp) =>
          myApp.run
      })
    }

    "Lifecycle API should be compatible with provider and instance bindings of type ZManaged" in {
      val resResource: ZManaged[Any, Throwable, Res1] = ZManaged.acquireReleaseWith(
        acquire = ZIO.attempt {
          val res = new Res1; res.initialized = true; res
        }
      )(release = res => ZIO.succeed(res.initialized = false))

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

      def assert1(ctx: Locator) = {
        ZIO.attempt {
          val i1 = ctx.get[Res]("instance")
          val i2 = ctx.get[Res]("provider")
          assert(!(i1 eq i2))
          assert(i1.initialized && i2.initialized)
          Then("ok")
          i1 -> i2
        }
      }

      def assert2(i1: Res, i2: Res) = {
        ZIO.attempt(assert(!i1.initialized && !i2.initialized))
      }

      def produceBIO[F[+_, +_]: TagKK: IO2] = injector.produceCustomF[F[Throwable, _]](plan)

      val ctxResource = produceBIO[IO]

      unsafeRun {
        ctxResource
          .use(assert1)
          .flatMap((assert2 _).tupled)
      }

      unsafeRun {
        import izumi.functional.bio.catz.BIOToMonadCancel
        ctxResource.toCats
          .use(assert1)
          .flatMap((assert2 _).tupled)
      }
    }

    "Conversions from ZManaged should fail to typecheck if the result type is unrelated to the binding type" in {
      assertCompiles(
        """
         new ModuleDef {
           make[String].fromResource { (_: Unit) => ZManaged.succeed("42") }
         }
      """
      )
      val res = intercept[TestFailedException](
        assertCompiles(
          """
         new ModuleDef {
           make[String].fromResource { (_: Unit) => ZManaged.succeed(42) }
         }
      """
        )
      )
      assert(res.getMessage contains "implicit")
      assert(res.getMessage contains "AdaptFunctoid")
    }

  }

  "ZLayer" should {
    "ZLayer works" in {
      val dbResource = ZLayer.scoped(ZIO.acquireRelease(ZIO.attempt {
        println("Connecting to DB!")
        new DBConnection
      })(_ => ZIO.succeed(println("Disconnecting DB"))))
      val mqResource = ZLayer.scoped(ZIO.acquireRelease(ZIO.attempt {
        println("Connecting to Message Queue!")
        new MessageQueueConnection
      })(_ => ZIO.succeed(println("Disconnecting Message Queue"))))

      val module = new ModuleDef {
        make[DBConnection].fromResource(dbResource)
        make[MessageQueueConnection].fromResource(mqResource)
        make[MyApp]
      }

      unsafeRun(Injector[Task]().produceRun(module) {
        (myApp: MyApp) =>
          myApp.run
      })
    }

    "Lifecycle API should be compatible with provider and instance bindings of type ZLayer" in {
      val resResource: ZLayer[Any, Throwable, Res1] = ZLayer.scoped(
        ZIO.acquireRelease(
          acquire = ZIO.attempt {
            val res = new Res1; res.initialized = true; res
          }
        )(release = res => ZIO.succeed(res.initialized = false))
      )

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

      def assert1(ctx: Locator) = {
        ZIO.attempt {
          val i1 = ctx.get[Res]("instance")
          val i2 = ctx.get[Res]("provider")
          assert(!(i1 eq i2))
          assert(i1.initialized && i2.initialized)
          Then("ok")
          i1 -> i2
        }
      }

      def assert2(i1: Res, i2: Res) = {
        ZIO.attempt(assert(!i1.initialized && !i2.initialized))
      }

      def produceBIO[F[+_, +_]: TagKK: IO2] = injector.produceCustomF[F[Throwable, _]](plan)

      val ctxResource = produceBIO[IO]

      unsafeRun {
        ctxResource
          .use(assert1)
          .flatMap((assert2 _).tupled)
      }

      unsafeRun {
        import izumi.functional.bio.catz.BIOToMonadCancel
        ctxResource.toCats
          .use(assert1)
          .flatMap((assert2 _).tupled)
      }
    }

    "Conversions from ZLayer should fail to typecheck if the result type is unrelated to the binding type" in {
      assertCompiles(
        """
         new ModuleDef {
           make[String].fromResource { (_: Unit) => ZLayer.succeed("42") }
         }
      """
      )
      val res = intercept[TestFailedException](
        assertCompiles(
          """
         new ModuleDef {
           make[String].fromResource { (_: Unit) => ZLayer.succeed(42) }
         }
      """
        )
      )
      assert(res.getMessage contains "implicit")
      assert(res.getMessage contains "AdaptFunctoid")
    }

    "Lifecycle.fromZIO(ZManaged.fork) is interruptible (https://github.com/7mind/izumi/issues/1138)" in {
      When("ZManaged is interruptible")
      unsafeRun(
        ZManaged
          .fromZIO(ZIO.never)
          .onExit((_: zio.Exit[Nothing, Unit]) => ZIO.succeed(Then("ZManaged interrupted")))
          .fork
          .use((_: Fiber[Nothing, Unit]).interrupt.unit)
      )

      When("Lifecycle is also interruptible")
      unsafeRun(
        Lifecycle
          .fromZIO {
            ZManaged
              .fromZIO(ZIO.never)
              .onExit((_: zio.Exit[Nothing, Unit]) => ZIO.succeed(Then("Lifecycle interrupted")))
              .fork
          }.use((_: Fiber[Nothing, Unit]).interrupt.unit)
      )

      When("Even `ZManaged -> Resource -> Lifecycle` chain is still interruptible")
      unsafeRun {
        import zio.interop.catz.*
        Lifecycle
          .fromCats[ZIO[Any, Throwable, _], Fiber[Nothing, Unit]](
            ZManaged
              .fromZIO(ZIO.never)
              .onExit((_: zio.Exit[Throwable, Unit]) => ZIO.succeed(Then("Resource interrupted")))
              .fork.toResourceZIO.mapK(FunctionK.id[Task].widen[ZIO[Any, Throwable, _]])
          ).use((_: Fiber[Throwable, Unit]).interrupt.unit)
      }
    }

    "In fa.flatMap(fb), fa and fb retain original interruptibility" in {
      Then("Lifecycle.fromZIO(_).flatMap is interruptible")
      unsafeRun(
        Lifecycle
          .fromZIO[Any, Throwable, Fiber[Nothing, Unit]](
            ZManaged
              .fromZIO(ZIO.never)
              .onExit((_: zio.Exit[Nothing, Unit]) => ZIO.succeed(Then("ZManaged interrupted")))
              .fork
          )
          .flatMap(a => Lifecycle.unit[Task].map(_ => a))
          .use((_: Fiber[Nothing, Unit]).interrupt.unit)
      )

      Then("_.flatMap(_ => Lifecycle.fromZIO(_)) is interruptible")
      unsafeRun(
        Lifecycle
          .unit[Task].flatMap {
            _ =>
              Lifecycle
                .fromZIO[Any, Throwable, Fiber[Nothing, Unit]](
                  ZManaged
                    .fromZIO(ZIO.never)
                    .onExit((_: zio.Exit[Nothing, Unit]) => ZIO.succeed(Then("ZManaged interrupted")))
                    .fork
                )
          }.use((_: Fiber[Nothing, Unit]).interrupt.unit)
      )
    }

  }

}
