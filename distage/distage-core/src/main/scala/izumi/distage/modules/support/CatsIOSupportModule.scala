package izumi.distage.modules.support

import cats.Parallel
import cats.effect.IO
import cats.effect.kernel.Async
import cats.effect.unsafe.{IORuntime, IORuntimeConfig, Scheduler}
import izumi.distage.model.definition.{Id, Lifecycle, ModuleDef}
import izumi.distage.model.effect.QuasiIORunner
import izumi.distage.modules.platform.CatsIOPlatformDependentSupportModule

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.ExecutionContext

object CatsIOSupportModule extends CatsIOSupportModule

/**
  * `cats.effect.IO` effect type support for `distage` resources, effects, roles & tests
  *
  *  - Adds [[izumi.distage.model.effect.QuasiIO]] instances to support using `cats.effect.IO` in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  *  - Adds `cats-effect` typeclass instances for `cats.effect.IO`
  *
  * Will also add the following components:
  *   - [[cats.effect.Blocker]] by using [[cats.effect.Blocker.apply]]
  *
  * Added into scope by [[izumi.distage.modules.DefaultModule]].
  *
  * Bindings to the same keys in your own [[izumi.distage.model.definition.ModuleDef]] or plugins will override these defaults.
  */
trait CatsIOSupportModule extends ModuleDef with CatsIOPlatformDependentSupportModule {
  // QuasiIO & cats-effect instances
  include(AnyCatsEffectSupportModule[IO])

  make[QuasiIORunner[IO]].from(QuasiIORunner.mkFromCatsIORuntime _)

  make[Async[IO]].from(IO.asyncForIO)
  make[Parallel[IO]].from(IO.parallelForIO)

  make[IORuntimeConfig].from(IORuntimeConfig())
  // by-name cycles don't work reliably at all, so unfortunately, manual cycle breaking:
  make[(IORuntime, ExecutionContext)].fromResource {
    (blockingPool: ExecutionContext @Id("io"), scheduler: Scheduler, ioRuntimeConfig: IORuntimeConfig) =>
      val cpuRef = new AtomicReference[ExecutionContext](null)
      lazy val ioRuntime: IORuntime = IORuntime(cpuRef.get(), blockingPool, scheduler, () => (), ioRuntimeConfig)
      createCPUPool(ioRuntime).map {
        ec =>
          cpuRef.set(ec)
          (ioRuntime, ec)
      }
  }
  make[IORuntime].from((_: (IORuntime, ExecutionContext))._1)
  make[ExecutionContext].named("cpu").from((_: (IORuntime, ExecutionContext))._2)

  make[Scheduler].fromResource {
    Lifecycle
      .makeSimple(
        acquire = Scheduler.createDefaultScheduler()
      )(release = _._2.apply()).map(_._1)
  }
}
