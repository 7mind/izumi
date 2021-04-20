package izumi.distage.modules.support

import cats.effect.{Concurrent, ConcurrentEffect, Effect, Sync}
import cats.{Applicative, Parallel}
import distage.{ModuleDef, TagK}
import izumi.distage.model.effect.{QuasiApplicative, QuasiAsync, QuasiIO, QuasiIORunner}
import izumi.distage.modules.typeclass.CatsEffectInstancesModule
import izumi.functional.mono.{Clock, Entropy, SyncSafe}
import izumi.fundamentals.platform.functional.Identity
import cats.effect.Temporal

/**
  * Any `cats-effect` effect type support for `distage` resources, effects, roles & tests.
  *
  * For all `F[_]` with available `make[ConcurrentEffect[F]]`, `make[Parallel[F]]` and `make[Timer[F]]` bindings.
  *
  *  - Adds [[izumi.distage.model.effect.QuasiIO]] instances to support using `F[_]` in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  *  - Adds `cats-effect` typeclass instances for `F[_]`
  *
  * Depends on `make[ConcurrentEffect[F]]`, `make[Parallel[F]]`, `make[Timer[F]]`.
  */
class AnyCatsEffectSupportModule[F[_]: TagK] extends ModuleDef {
  include(CatsEffectInstancesModule[F])

  make[QuasiIORunner[F]].from {
    implicit F: Effect[F] => QuasiIORunner.fromCats
  }
  make[QuasiIO[F]].from {
    implicit F: Sync[F] => QuasiIO.fromCats
  }
  make[QuasiAsync[F]].from {
    (C0: Concurrent[F], T0: Temporal[F], P0: Parallel[F]) =>
      implicit val C: Concurrent[F] = C0
      implicit val T: Temporal[F] = T0
      implicit val P: Parallel[F] = P0
      QuasiAsync.fromCats
  }
  make[QuasiApplicative[F]].from {
    implicit F: Applicative[F] => QuasiApplicative.fromCats
  }
  make[SyncSafe[F]].from {
    implicit F: Sync[F] => SyncSafe.fromSync
  }
  make[Clock[F]].from {
    Clock.fromImpure(_: Clock[Identity])(_: SyncSafe[F])
  }
  make[Entropy[F]].from {
    Entropy.fromImpure(_: Entropy[Identity])(_: SyncSafe[F])
  }
}

object AnyCatsEffectSupportModule {
  @inline def apply[F[_]: TagK]: AnyCatsEffectSupportModule[F] = new AnyCatsEffectSupportModule[F]

  /**
    * Make [[AnyCatsEffectSupportModule]], binding the required dependencies in place to values from implicit scope
    *
    * `make[ContextShift[F]]` is not required by [[AnyCatsEffectSupportModule]] but is added for completeness
    */
  def withImplicits[F[_]: TagK: ConcurrentEffect: Parallel: Temporal: ContextShift]: ModuleDef = new ModuleDef {
    addImplicit[ConcurrentEffect[F]]
    addImplicit[Parallel[F]]
    addImplicit[Temporal[F]]
    addImplicit[ContextShift[F]]

    include(AnyCatsEffectSupportModule[F])
  }
}
