package izumi.distage.effect.modules

import cats.effect.{Concurrent, ConcurrentEffect, ContextShift, Effect, Sync, Timer}
import cats.{Applicative, Parallel}
import distage.{ModuleDef, TagK}
import izumi.distage.model.effect.{DIApplicative, DIEffect, DIEffectAsync, DIEffectRunner}
import izumi.functional.mono.SyncSafe

/** Any `cats-effect` effect type support for `distage` resources, effects, roles & tests.
  *
  * For any `F[_]` with available `make[ConcurrentEffect[F]]`, `make[Parallel[F]]` and `make[Timer[F]]` bindings.
  *
  * - Adds [[izumi.distage.model.effect.DIEffect]] instances to support using `F[_]` in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  * - Adds [[cats.effect]] typeclass instances for `F[_]`
  *
  * Depends on `make[ConcurrentEffect[F]]`, `make[Parallel[F]]` and `make[Timer[F]]`
  */
class PolymorphicCatsDIEffectModule[F[_]: TagK] extends ModuleDef {
  include(PolymorphicCatsTypeclassesModule[F])

  make[DIEffectRunner[F]].from {
    implicit F: Effect[F] => DIEffectRunner.fromCats
  }
  make[DIEffect[F]].from {
    implicit F: Sync[F] => DIEffect.fromCats
  }
  make[DIEffectAsync[F]].from {
    (C0: Concurrent[F], T0: Timer[F], P0: Parallel[F]) =>
      implicit val C: Concurrent[F] = C0
      implicit val T: Timer[F] = T0
      implicit val P: Parallel[F] = P0
      DIEffectAsync.fromCats
  }
  make[DIApplicative[F]].from {
    implicit F: Applicative[F] => DIApplicative.fromCats
  }
  make[SyncSafe[F]].from {
    implicit F: Sync[F] => SyncSafe.fromSync
  }
}

object PolymorphicCatsDIEffectModule {
  @inline def apply[F[_]: TagK]: PolymorphicCatsDIEffectModule[F] = new PolymorphicCatsDIEffectModule[F]

  /** Make [[PolymorphicCatsDIEffectModule]], binding the required dependencies in place to values from implicit scope */
  def withImplicits[F[_]: TagK: ConcurrentEffect: Parallel: Timer: ContextShift]: ModuleDef = new ModuleDef {
    addImplicit[ConcurrentEffect[F]]
    addImplicit[Parallel[F]]
    addImplicit[Timer[F]]
    addImplicit[ContextShift[F]]

    include(PolymorphicCatsDIEffectModule[F])
  }
}
