package izumi.distage.compat

import cats.effect.{Bracket, IO, Resource, Sync}
import distage._
import izumi.distage.compat.CatsResourcesTest._
import izumi.distage.model.definition.Binding.SingletonBinding
import izumi.distage.model.definition.{Activation, DIResource, ImplDef, ModuleDef}
import izumi.distage.model.effect.LowPriorityDIEffectInstances
import izumi.distage.model.plan.GCMode
import izumi.fundamentals.platform.language.unused
import org.scalatest.GivenWhenThen
import org.scalatest.exceptions.TestFailedException
import org.scalatest.wordspec.AnyWordSpec

object CatsResourcesTest {
  class Res { var initialized = false }
  class Res1 extends Res

  class DBConnection
  class MessageQueueConnection

  class MyApp(@unused db: DBConnection, @unused mq: MessageQueueConnection) {
    val run = IO(println("Hello World!"))
  }
}

final class CatsResourcesTest extends AnyWordSpec with GivenWhenThen {

  "`No More Orphans` type provider is accessible" in {
    def y[R[_[_]]: LowPriorityDIEffectInstances._Sync]() = ()
    y()
  }

  "cats.Resource mdoc example works" in {
    val dbResource = Resource.make(IO { println("Connecting to DB!"); new DBConnection })(_ => IO(println("Disconnecting DB")))
    val mqResource = Resource.make(IO { println("Connecting to Message Queue!"); new MessageQueueConnection })(_ => IO(println("Disconnecting Message Queue")))

    val module = new ModuleDef {
      make[DBConnection].fromResource(dbResource)
      make[MessageQueueConnection].fromResource(mqResource)
      addImplicit[Bracket[IO, Throwable]]
      make[MyApp]
    }

    Injector()
      .produceF[IO](module, Activation.empty, GCMode.NoGC).use {
        objects =>
          objects.get[MyApp].run
      }.unsafeRunSync()
  }

  "DIResource API should be compatible with provider and instance bindings of type cats.effect.Resource" in {
    val resResource: Resource[IO, Res1] = Resource.make(
      acquire = IO { val res = new Res1; res.initialized = true; res }
    )(release = res => IO(res.initialized = false))

    val definition: ModuleDef = new ModuleDef {
      make[Res].named("instance").fromResource(resResource)

      make[Res].named("provider").fromResource {
        _: Res @Id("instance") =>
          resResource
      }
    }

    definition.bindings.foreach {
      case SingletonBinding(_, implDef @ ImplDef.ResourceImpl(_, _, ImplDef.ProviderImpl(providerImplType, fn)), _, _) =>
        assert(implDef.implType == SafeType.get[Res1])
        assert(providerImplType == SafeType.get[DIResource.FromCats[IO, Res1]])
        assert(fn.diKeys contains DIKey.get[Bracket[IO, Throwable]])
      case _ =>
        fail()
    }

    val injector = Injector()
    val plan = injector.plan(PlannerInput.noGC(definition ++ new ModuleDef {
      addImplicit[Bracket[IO, Throwable]]
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

    def produceSync[F[_]: TagK: Sync] = injector.produceF[F](plan)

    val ctxResource = produceSync[IO]

    ctxResource
      .use(assert1)
      .flatMap((assert2 _).tupled)
      .unsafeRunSync()

    ctxResource
      .toCats
      .use(assert1)
      .flatMap((assert2 _).tupled)
      .unsafeRunSync()
  }

  "Conversions from cats-effect Resource should fail to typecheck if the result type is unrelated to the binding type" in {
    assertCompiles(
      """
         new ModuleDef {
           make[String].fromResource { (_: Unit) => Resource.pure("42") }
         }
      """
    )
    val res = intercept[TestFailedException](
      assertCompiles(
        """
         new ModuleDef {
           make[String].fromResource { (_: Unit) => Resource.pure(42) }
         }
      """
      )
    )
    assert(res.getMessage contains "could not find implicit value for parameter adapt: izumi.distage.model.definition.DIResource.AdaptProvider.Aux")
  }

}
