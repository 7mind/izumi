package izumi.distage.compat

import cats.effect.std.Dispatcher
import distage.{DIKey, DefaultModule, Injector, Module, Roots, TagKK}
import izumi.distage.injector.MkInjector
import izumi.distage.modules.support.ZIOSupportModule
import izumi.distage.modules.typeclass.BIOInstancesModule
import izumi.functional.bio.{Bifunctorized, IO2, Primitives2, UnsafeRun2}
import izumi.functional.bio.CatsToBIOConversions.*
import org.scalatest.wordspec.AnyWordSpec
import zio.{ZEnvironment, ZLayer}

final class DefaultModuleTest extends AnyWordSpec with MkInjector with CatsIOPlatformDependentTest with ZIOTest {

  "Default modules" should {

    "build for forZIOPlusCats" in {
      unsafeRun(
        Injector[zio.ZIO[Any, +_, +_]]()(using
          summon[IO2[zio.ZIO[Any, +_, +_]]],
          summon[Primitives2[zio.ZIO[Any, +_, +_]]],
          summon[TagKK[zio.ZIO[Any, +_, +_]]],
          DefaultModule.forZIOPlusCats[zio.interop.CatsIOResourceSyntax, cats.effect.kernel.Async, zio.ZIO, Any]
            .asInstanceOf[DefaultModule[zio.ZIO[Any, +_, +_]]],
        )
          .produce(Module.empty, Roots.Everything).unsafeGet()
      )
    }

    "build for forZIO" in {
      unsafeRun(
        Injector[zio.ZIO[Any, +_, +_]]()(using
          summon[IO2[zio.ZIO[Any, +_, +_]]],
          summon[Primitives2[zio.ZIO[Any, +_, +_]]],
          summon[TagKK[zio.ZIO[Any, +_, +_]]],
          DefaultModule.forZIO[zio.ZIO, Any],
        )
          .produce(Module.empty, Roots.Everything).unsafeGet()
      )
    }

    "build for forCatsIO" in {
      catsIOUnsafeRunSync(
        Injector[Bifunctorized[cats.effect.IO, +_, +_]]()(using
          summon[IO2[Bifunctorized[cats.effect.IO, +_, +_]]],
          summon[Primitives2[Bifunctorized[cats.effect.IO, +_, +_]]],
          summon[TagKK[Bifunctorized[cats.effect.IO, +_, +_]]],
          DefaultModule.forCatsIO[cats.effect.IO],
        )
          .produce(Module.empty, Roots.Everything).unsafeGet()
      )
    }

    "build for fromBIO" in {
      implicit val unsafeRun2: UnsafeRun2[zio.IO] = UnsafeRun2.createZIO()
      unsafeRun(
        Injector[zio.ZIO[Any, +_, +_]]()(using
          summon[IO2[zio.ZIO[Any, +_, +_]]],
          summon[Primitives2[zio.ZIO[Any, +_, +_]]],
          summon[TagKK[zio.ZIO[Any, +_, +_]]],
          DefaultModule.fromBIO[zio.IO],
        )
          .produce(Module.empty, Roots.Everything).unsafeGet()
      )
    }

    "build for fromCats" in {
      catsIOUnsafeRunSync {
        Dispatcher.sequential[cats.effect.IO].use {
          implicit dispatcher =>
            Injector[Bifunctorized[cats.effect.IO, +_, +_]]()(using
              summon[IO2[Bifunctorized[cats.effect.IO, +_, +_]]],
              summon[Primitives2[Bifunctorized[cats.effect.IO, +_, +_]]],
              summon[TagKK[Bifunctorized[cats.effect.IO, +_, +_]]],
              DefaultModule.fromCats[cats.effect.IO, cats.effect.kernel.Async, cats.Parallel, Dispatcher],
            )
              .produce(Module.empty, Roots.Everything).unsafeGet()
        }
      }
    }

    "ZIOSupportModule contains at least as many algebras as BIOInstancesModule" in {
      val ZIOSupportModuleAny = ZIOSupportModule[Any]
      val ZIOSupportModuleInt = ZIOSupportModule[Int]

      val instancesAny = {
        implicit val unsafeRun2: UnsafeRun2.ZIORunner[Any] = new UnsafeRun2.ZIORunner[Any](ZLayer.empty, ZEnvironment.empty)
        BIOInstancesModule.auxAlgebrasImplicits[zio.IO]
      }

      val instancesInt = {
        implicit val unsafeRun2: UnsafeRun2.ZIORunner[Int] = new UnsafeRun2.ZIORunner[Int](ZLayer.empty, ZEnvironment(1))
        BIOInstancesModule.auxAlgebrasImplicits[zio.ZIO[Int, +_, +_]]
      }

      assert((instancesAny.keys -- ZIOSupportModuleAny.keys) == Set.empty)
      assert((instancesInt.keys -- ZIOSupportModuleInt.keys) == Set.empty)
      assert(((instancesAny.keys - DIKey[UnsafeRun2[zio.IO]]) -- ZIOSupportModuleInt.keys) == Set.empty)
    }

  }

}
