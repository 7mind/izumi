package izumi.distage.impl

import distage.Injector
import izumi.distage.modules.DefaultModule
import izumi.functional.bio.impl.MiniBIOAsync
import izumi.functional.bio.{F, Monad2, Primitives2}
import izumi.functional.quasi.QuasiIORunner
import izumi.fundamentals.platform.functional.{Identity, Identity2}
import izumi.distage.testkit.runner.spec.AnyWordSpec


class OptionalDependencyTest extends AnyWordSpec {

  "test 1" in {
    def adder[F[+_, +_]: Monad2: Primitives2](i: Int): F[Nothing, Int] = {
      F.mkRef(0)
        .flatMap(ref => ref.update(_ + i) *> ref.get)
    }

    locally {
      implicit val bioMonad: Monad2[Identity2] = null
      implicit val primitives: Primitives2[Identity2] = null
      intercept[NullPointerException](adder[Identity2](0))
    }
  }

  "Using DefaultModules" in {
    def getDefaultModules[F[_]: DefaultModule]: DefaultModule[F] = implicitly
    def getDefaultModulesOrEmpty[F[_]](implicit m: DefaultModule[F] = DefaultModule.empty[F]): DefaultModule[F] = m

    val defaultModules = getDefaultModules
    assert((defaultModules: DefaultModule[Identity]).getClass == DefaultModule.forIdentity.getClass)

    val empty = getDefaultModulesOrEmpty[Option]
    assert(empty.module.bindings.isEmpty)
  }

  "MiniBIOAsync has DefaultModule" in {
    import scala.concurrent.ExecutionContext.Implicits.global

    implicitly[DefaultModule[MiniBIOAsync[Throwable, _]]]

    Injector[MiniBIOAsync[Throwable, _]]().produceRun(distage.Module.empty) {
      (runner: QuasiIORunner[MiniBIOAsync[Throwable, _]]) =>
        MiniBIOAsync.WeakAsyncForMiniBIOAsync.syncBlocking {
          runner.runBlocking(MiniBIOAsync.WeakAsyncForMiniBIOAsync.pure(()))
        }
    }
  }

  "Using Lifecycle & QuasiIO objects succeeds even if there's no cats/zio/monix on the classpath" in {
    OptionalDependencyIsolation.run("izumi.distage.impl.LifecycleQuasiIOWithoutEffects")
  }

  "All bio objects with zio-specific defs initialize even if there's no zio on the classpath" in {
    OptionalDependencyIsolation.run("izumi.distage.impl.ZIOObjectsWithoutZIO")
  }

  "All bio objects with cats-specific defs initialize even if there's no cats on the classpath" in {
    OptionalDependencyIsolation.run("izumi.distage.impl.CatsObjectsWithoutCats")
  }

  "Using Exit.Trace succeeds even if there's no zio on the classpath" in {
    OptionalDependencyIsolation.run("izumi.distage.impl.ExitTraceWithoutZIO")
  }

}
