package izumi.distage.compat

import cats.effect.std.Dispatcher
import cats.effect.unsafe.IORuntime
import distage.{DIKey, DefaultModule, Injector, Module, Roots, TagK}
import izumi.distage.injector.MkInjector
import izumi.distage.modules.support.ZIOSupportModule
import izumi.distage.modules.typeclass.BIOInstancesModule
import izumi.functional.bio.UnsafeRun2
import izumi.functional.quasi.{QuasiIO, QuasiIORunner}
import org.scalatest.wordspec.AnyWordSpec
import zio.{ZEnvironment, ZLayer}

final class DefaultModuleTest extends AnyWordSpec with MkInjector with CatsIOPlatformDependentTest with ZIOTest {

  "Default modules" should {

    "build for forZIOPlusCats" in {
      unsafeRun(
        Injector[zio.Task]()(using implicitly[QuasiIO[zio.Task]], implicitly[TagK[zio.Task]], DefaultModule.forZIOPlusCats)
          .produce(Module.empty, Roots.Everything).unsafeGet()
      )
    }

    "build for forZIO" in {
      unsafeRun(
        Injector[zio.Task]()(using implicitly[QuasiIO[zio.Task]], implicitly[TagK[zio.Task]], DefaultModule.forZIO).produce(Module.empty, Roots.Everything).unsafeGet()
      )
    }

    "build for forCatsIO" in {
      catsIOUnsafeRunSync(
        Injector[cats.effect.IO]()(using implicitly[QuasiIO[cats.effect.IO]], implicitly[TagK[cats.effect.IO]], DefaultModule.forCatsIO)
          .produce(Module.empty, Roots.Everything).unsafeGet()
      )
    }

    "build for fromBIO" in {
      implicit val unsafeRun2: UnsafeRun2[zio.IO] = UnsafeRun2.createZIO()
      unsafeRun(
        Injector[zio.Task]()(using implicitly[QuasiIO[zio.Task]], implicitly[TagK[zio.Task]], DefaultModule.fromBIO[zio.IO])
          .produce(Module.empty, Roots.Everything).unsafeGet()
      )
    }

    "build for fromCats" in {
      catsIOUnsafeRunSync {
        Dispatcher.sequential[cats.effect.IO].use {
          implicit dispatcher =>
            Injector[cats.effect.IO]()(using implicitly[QuasiIO[cats.effect.IO]], implicitly[TagK[cats.effect.IO]], DefaultModule.fromCats: DefaultModule[cats.effect.IO])
              .produce(Module.empty, Roots.Everything).unsafeGet()
        }
      }
    }

    "build for fromQuasiIO" in {
      implicit val quasiIORunner: QuasiIORunner[cats.effect.IO] = QuasiIORunner.mkFromCatsIORuntime(IORuntime.builder().build())
      catsIOUnsafeRunSync(
        Injector[cats.effect.IO]()(using implicitly[QuasiIO[cats.effect.IO]], implicitly[TagK[cats.effect.IO]], DefaultModule.fromQuasiIO: DefaultModule[cats.effect.IO])
          .produce(Module.empty, Roots.Everything).unsafeGet()
      )
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
