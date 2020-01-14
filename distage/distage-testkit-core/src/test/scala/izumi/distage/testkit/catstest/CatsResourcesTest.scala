package izumi.distage.testkit.catstest

import cats.effect.{Bracket, IO, Resource, Sync}
import distage._
import izumi.distage.model.definition.Binding.SingletonBinding
import izumi.distage.model.definition.{DIResource, ImplDef, ModuleDef}
import izumi.distage.model.effect.LowPriorityDIEffectInstances
import izumi.distage.model.plan.GCMode
import izumi.distage.testkit.catstest.CatsResourcesTest._
import izumi.fundamentals.platform.language.Quirks._
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

object CatsResourcesTest {
  class Res { var initialized = false }
  class Res1 extends Res

  class DBConnection
  class MessageQueueConnection

  class MyApp(db: DBConnection, mq: MessageQueueConnection) {
    db.discard()
    mq.discard()

    val run = IO(println("Hello World!"))
  }
}

class CatsResourcesTest extends AnyWordSpec with GivenWhenThen {

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

    Injector().produceF[IO](module, GCMode.NoGC).use {
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
      case SingletonBinding(_, implDef@ImplDef.ResourceImpl(_, _, ImplDef.ProviderImpl(providerImplType, fn)), _, _) =>
        assert(implDef.implType == SafeType.get[Res1])
        assert(providerImplType == SafeType.get[DIResource.Cats[IO, Res1]])
        assert(fn.diKeys contains DIKey.get[Bracket[IO, Throwable]])
      case _ =>
        fail()
    }

    val injector = Injector()
    val plan = injector.plan(PlannerInput.noGc(definition ++ new ModuleDef {
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

}
