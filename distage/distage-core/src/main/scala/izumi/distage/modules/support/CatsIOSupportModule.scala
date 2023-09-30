package izumi.distage.modules.support

import cats.Parallel
import cats.effect.IO
import cats.effect.kernel.Async
import cats.effect.unsafe.{IORuntimeConfig, Scheduler}
import izumi.distage.model.definition.{Lifecycle, ModuleDef}
import izumi.distage.modules.platform.CatsIOPlatformDependentSupportModule
import izumi.functional.quasi.QuasiIORunner

object CatsIOSupportModule extends CatsIOSupportModule

/**
  * `cats.effect.IO` effect type support for `distage` resources, effects, roles & tests
  *
  *  - Adds [[izumi.functional.quasi.QuasiIO]] instances to support using `cats.effect.IO` in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  *  - Adds `cats-effect` typeclass instances for `cats.effect.IO`
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

  make[Scheduler].fromResource {
    Lifecycle
      .makeSimple(
        acquire = Scheduler.createDefaultScheduler()
      )(release = _._2.apply()).map(_._1)
  }
}
