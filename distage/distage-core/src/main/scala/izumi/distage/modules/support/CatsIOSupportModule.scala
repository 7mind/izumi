package izumi.distage.modules.support

import cats.Parallel
import cats.effect.IO
import cats.effect.kernel.Async
import cats.effect.unsafe.{IORuntimeConfig, Scheduler}
import izumi.distage.model.definition.{Lifecycle, ModuleDef}
import izumi.distage.modules.platform.CatsIOPlatformDependentSupportModule

object CatsIOSupportModule extends CatsIOSupportModule

/**
  * `cats.effect.IO` effect type support for `distage` resources, effects, roles & tests
  *
  *  - Adds [[izumi.functional.bio]] bifunctor BIO instances on `Bifunctorized[cats.effect.IO, +_, +_]`
  *  - Adds `cats-effect` typeclass instances for `cats.effect.IO`
  *
  * Added into scope by [[izumi.distage.modules.DefaultModule]].
  *
  * Bindings to the same keys in your own [[izumi.distage.model.definition.ModuleDef]] or plugins will override these defaults.
  */
trait CatsIOSupportModule extends ModuleDef with CatsIOPlatformDependentSupportModule {
  // Bifunctor BIO + cats-effect instances on cats.effect.IO
  include(AnyCatsEffectSupportModule.usingAsyncParallel[IO])

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
