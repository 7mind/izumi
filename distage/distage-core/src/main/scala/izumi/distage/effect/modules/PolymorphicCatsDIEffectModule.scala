package izumi.distage.effect.modules

import cats.effect.{Concurrent, Effect, Sync, Timer}
import cats.{Applicative, Parallel}
import distage.{ModuleDef, TagK}
import izumi.distage.model.effect.{DIApplicative, DIEffect, DIEffectAsync, DIEffectRunner}
import izumi.functional.mono.SyncSafe

/**
  * Module definition that binds effect TC instances for an arbitrary F[_].
  */
class PolymorphicCatsDIEffectModule[F[_]: TagK] extends ModuleDef {
  include(PolymorphicCatsTypeclassesModule[F])

  make[DIEffectRunner[F]].from {
    implicit F: Effect[F] => DIEffectRunner.fromCats
  }
  make[DIEffect[F]].from {
    implicit F: Sync[F] => DIEffect.fromCatsEffect
  }
  make[DIEffectAsync[F]].from {
    (P0: Parallel[F], T0: Timer[F], C0: Concurrent[F]) =>
      implicit val P: Parallel[F] = P0
      implicit val T: Timer[F] = T0
      implicit val C: Concurrent[F] = C0
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
  def apply[F[_]: TagK]: PolymorphicCatsDIEffectModule[F] = new PolymorphicCatsDIEffectModule[F]
}
