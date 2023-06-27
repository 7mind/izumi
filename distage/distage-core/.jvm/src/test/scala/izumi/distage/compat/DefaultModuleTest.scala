package izumi.distage.compat

import cats.effect.std.Dispatcher
import cats.effect.unsafe.IORuntime
import distage.{DefaultModule, Injector, Module, Roots, TagK}
import izumi.distage.injector.MkInjector
import izumi.functional.bio.UnsafeRun2
import izumi.functional.quasi.{QuasiIO, QuasiIORunner}
import org.scalatest.wordspec.AnyWordSpec

final class DefaultModuleTest extends AnyWordSpec with MkInjector with CatsIOPlatformDependentTest with ZIOTest {

  "Default modules" should {

    "build for forZIOPlusCats" in {
      unsafeRun(
        Injector[zio.Task]()(implicitly[QuasiIO[zio.Task]], implicitly[TagK[zio.Task]], DefaultModule.forZIOPlusCats).produce(Module.empty, Roots.Everything).unsafeGet()
      )
    }

    "build for forZIO" in {
      unsafeRun(Injector[zio.Task]()(implicitly[QuasiIO[zio.Task]], implicitly[TagK[zio.Task]], DefaultModule.forZIO).produce(Module.empty, Roots.Everything).unsafeGet())
    }

    "build for forCatsIO" in {
      catsIOUnsafeRunSync(
        Injector[cats.effect.IO]()(implicitly[QuasiIO[cats.effect.IO]], implicitly[TagK[cats.effect.IO]], DefaultModule.forCatsIO)
          .produce(Module.empty, Roots.Everything).unsafeGet()
      )
    }

    "build for fromBIO2" in {
      implicit val unsafeRun2: UnsafeRun2[zio.IO] = UnsafeRun2.createZIO()
      unsafeRun(
        Injector[zio.Task]()(implicitly[QuasiIO[zio.Task]], implicitly[TagK[zio.Task]], DefaultModule.fromBIO2[zio.IO])
          .produce(Module.empty, Roots.Everything).unsafeGet()
      )
    }

//    "build for fromBIO3" in {
//      implicit val unsafeRun2: UnsafeRun3[zio.ZIO] = UnsafeRun2.createZIO()
//      unsafeRun(Injector[zio.Task]()(implicitly[QuasiIO[zio.Task]], implicitly[TagK[zio.Task]], DefaultModule.fromBIO3[zio.ZIO]).produce(Module.empty, Roots.Everything).unsafeGet())
//    }

    "build for fromCats" in {
      catsIOUnsafeRunSync {
        Dispatcher.sequential[cats.effect.IO].use {
          implicit dispatcher =>
            Injector[cats.effect.IO]()(implicitly[QuasiIO[cats.effect.IO]], implicitly[TagK[cats.effect.IO]], DefaultModule.fromCats: DefaultModule[cats.effect.IO])
              .produce(Module.empty, Roots.Everything).unsafeGet()
        }
      }
    }

    "build for fromQuasiIO" in {
      implicit val quasiIORunner: QuasiIORunner[cats.effect.IO] = QuasiIORunner.mkFromCatsIORuntime(IORuntime.builder().build())
      catsIOUnsafeRunSync(
        Injector[cats.effect.IO]()(implicitly[QuasiIO[cats.effect.IO]], implicitly[TagK[cats.effect.IO]], DefaultModule.fromQuasiIO: DefaultModule[cats.effect.IO])
          .produce(Module.empty, Roots.Everything).unsafeGet()
      )
    }

  }

}
