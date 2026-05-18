package izumi.distage.modules.support

import cats.Parallel
import cats.effect.IO
import cats.effect.kernel.Async
import cats.effect.unsafe.{IORuntime, IORuntimeConfig, Scheduler}
import izumi.distage.model.definition.{Lifecycle, ModuleDef}
import izumi.distage.modules.platform.CatsIOPlatformDependentSupportModule
import izumi.functional.bio.{Bifunctorized, UnsafeRun2}
import izumi.functional.bio.impl.CatsIORunnerPlatformSpecific
import izumi.reflect.TagK

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

  make[Scheduler].fromResource[izumi.functional.bio.Bifunctorized.IdentityBifunctorized, Throwable, Lifecycle[izumi.functional.bio.Bifunctorized.IdentityBifunctorized, Throwable, Scheduler]](
    Lifecycle
      .makeSimple(
        acquire = Scheduler.createDefaultScheduler()
      )(release = _._2.apply()).map(_._1): Lifecycle[izumi.functional.bio.Bifunctorized.IdentityBifunctorized, Throwable, Scheduler]
  )

  // UnsafeRun2 for the bifunctorized cats-effect IO is built from the `IORuntime` that is
  // bound by `CatsIOPlatformDependentSupportModule`. This replaces the missing entry that the
  // role-app launcher and testkit runner summon as `UnsafeRun2[F]` for `F = Bifunctorized[IO, +_, +_]`.
  // The JVM impl supports synchronous `unsafeRunSync`; the JS impl throws on it (mirroring the
  // existing platform limitation on `IO.unsafeRunSync` in JS).
  make[UnsafeRun2[Bifunctorized[IO, +_, +_]]].from {
    (rt: IORuntime) =>
      CatsIORunnerPlatformSpecific.fromIORuntime(using rt, TagK[IO])
  }
}
