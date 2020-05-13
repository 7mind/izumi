package izumi.distage.compat

import cats.effect.Bracket
import distage._
import izumi.distage.compat.ZIOResourcesTestJvm._
import izumi.distage.model.definition.Binding.SingletonBinding
import izumi.distage.model.definition.{Activation, DIResource, ImplDef, ModuleDef}
import izumi.distage.model.plan.GCMode
import izumi.functional.bio.BIO
import izumi.fundamentals.platform.language.unused
import org.scalatest.GivenWhenThen
import org.scalatest.exceptions.TestFailedException
import org.scalatest.wordspec.AnyWordSpec
import zio.Runtime.default.unsafeRun
import zio._

object ZIOResourcesTestJvm {
  class Res { var initialized = false }
  class Res1 extends Res

  class DBConnection
  class MessageQueueConnection

  class MyApp(@unused db: DBConnection, @unused mq: MessageQueueConnection) {
    val run = IO(println("Hello World!"))
  }
}
final class ZIOResourcesTestJvm extends AnyWordSpec with GivenWhenThen {

  "ZManaged" should {
    "ZManaged works" in {
      val dbResource = ZManaged.make(UIO {
        println("Connecting to DB!"); new DBConnection
      })(_ => UIO(println("Disconnecting DB")))
      val mqResource = ZManaged.make(IO {
        println("Connecting to Message Queue!"); new MessageQueueConnection
      })(_ => UIO(println("Disconnecting Message Queue")))

      val module = new ModuleDef {
        make[DBConnection].fromResource(dbResource)
        make[MessageQueueConnection].fromResource(mqResource)
        make[MyApp]
      }

      unsafeRun(Injector().produceF[Task](module, GCMode.NoGC).use {
        objects =>
          objects.get[MyApp].run
      })
    }

    "DIResource API should be compatible with provider and instance bindings of type ZManaged" in {
      val resResource: ZManaged[Any, Throwable, Res1] = ZManaged.make(
        acquire = IO {
          val res = new Res1; res.initialized = true; res
        }
      )(release = res => UIO(res.initialized = false))

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
          assert(providerImplType == SafeType.get[DIResource.FromZIO[Any, Throwable, Res1]])
          assert(!(fn.diKeys contains DIKey.get[Bracket[Task, Throwable]]))
        case _ =>
          fail()
      }

      val injector = Injector()
      val plan = injector.plan(PlannerInput.noGC(definition, Activation.empty))

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

      def produceBIO[F[+_, +_]: TagKK: BIO] = injector.produceF[F[Throwable, ?]](plan)

      val ctxResource = produceBIO[IO]

      unsafeRun {
        ctxResource
          .use(assert1)
          .flatMap((assert2 _).tupled)
      }

      unsafeRun {
        import izumi.functional.bio.catz.BIOToBracket
        ctxResource
          .toCats
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
      assert(res.getMessage contains "could not find implicit value for parameter adapt: izumi.distage.model.definition.DIResource.AdaptProvider.Aux")
    }

  }

  "ZLayer" should {
    "ZLayer works" in {
      val dbResource = ZLayer.fromAcquireRelease(UIO {
        println("Connecting to DB!"); new DBConnection
      })(_ => UIO(println("Disconnecting DB")))
      val mqResource = ZLayer.fromAcquireRelease(IO {
        println("Connecting to Message Queue!"); new MessageQueueConnection
      })(_ => UIO(println("Disconnecting Message Queue")))

      val module = new ModuleDef {
        make[DBConnection].fromResource(dbResource)
        make[MessageQueueConnection].fromResource(mqResource)
        make[MyApp]
      }

      unsafeRun(Injector().produceF[Task](module, GCMode.NoGC).use {
        objects =>
          objects.get[MyApp].run
      })
    }

    "DIResource API should be compatible with provider and instance bindings of type ZLayer" in {
      val resResource: ZLayer[Any, Throwable, Has[Res1]] = ZLayer.fromAcquireRelease(
        acquire = IO {
          val res = new Res1; res.initialized = true; res
        }
      )(release = res => UIO(res.initialized = false))

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
          assert(providerImplType == SafeType.get[DIResource.FromZIO[Any, Throwable, Res1]])
          assert(!(fn.diKeys contains DIKey.get[Bracket[Task, Throwable]]))
        case _ =>
          fail()
      }

      val injector = Injector()
      val plan = injector.plan(PlannerInput.noGC(definition, Activation.empty))

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

      def produceBIO[F[+_, +_]: TagKK: BIO] = injector.produceF[F[Throwable, ?]](plan)

      val ctxResource = produceBIO[IO]

      unsafeRun {
        ctxResource
          .use(assert1)
          .flatMap((assert2 _).tupled)
      }

      unsafeRun {
        import izumi.functional.bio.catz.BIOToBracket
        ctxResource
          .toCats
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
      assert(res.getMessage contains "could not find implicit value for parameter adapt: izumi.distage.model.definition.DIResource.AdaptProvider.Aux")
    }
  }

}
