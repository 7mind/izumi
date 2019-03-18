package com.github.pshirshov.izumi.distage

import cats.effect.{IO, Resource, Sync}
import com.github.pshirshov.izumi.distage.CatsResourcesTest._
import com.github.pshirshov.izumi.distage.model.PlannerInput
import com.github.pshirshov.izumi.distage.model.definition.Binding.SingletonBinding
import com.github.pshirshov.izumi.distage.model.definition.{DIResource, ImplDef, ModuleDef}
import com.github.pshirshov.izumi.distage.model.monadic.FromCats
import distage._
import org.scalatest.WordSpec

object CatsResourcesTest {
  class Res { var initialized = false }
  class Res1 extends Res
}

class CatsResourcesTest extends WordSpec {

  "`No More Orphans` type provider is accessible" in {
    def y[R[_[_]]: FromCats._Sync]() = ()
    y()
  }

  "DIResource API should be compatible with provider and instance bindings with cats.effect.Resource" in {
    val resResource: Resource[IO, Res1] = Resource.make(
      acquire = IO { val res = new Res1; res.initialized = true; res }
    )(release = res => IO(res.initialized = false))

    val definition: PlannerInput = PlannerInput(new ModuleDef {
      make[Res].named("instance").fromResource(resResource)

      make[Res].named("provider").fromResource {
        _: Res @Id("instance") =>
          resResource
      }
    })

    definition.bindings.bindings.foreach {
      case SingletonBinding(_, implDef: ImplDef.ResourceImpl, _, _) =>
        assert(implDef.implType == SafeType.get[Res1])
        assert(implDef.resourceImpl.implType == SafeType.get[DIResource.Cats[IO, Res1]])
      case _ =>
        fail()
    }

    val injector = Injector()
    val plan = injector.plan(definition)

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

    def produceSync[F[_]: TagK: Sync] = injector.produceF[F](plan)

    val ctxResource = produceSync[IO]

    ctxResource
      .use(assert1)
      .flatMap((assert2 _).tupled)

    ctxResource
      .toCats
      .use(assert1)
      .flatMap((assert2 _).tupled)
  }


}
