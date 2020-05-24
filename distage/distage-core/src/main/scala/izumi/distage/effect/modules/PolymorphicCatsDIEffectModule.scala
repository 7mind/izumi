package izumi.distage.effect.modules

import cats.Parallel
import cats.effect.{Concurrent, Effect, Sync, Timer}
import distage.{ModuleDef, TagK}
import izumi.distage.model.effect.{DIEffect, DIEffectAsync, DIEffectRunner}
import izumi.functional.mono.SyncSafe

/**
  * Module definition that binds effect TC instances for an arbitrary F[_].
  */
class PolymorphicCatsDIEffectModule[F[_]: TagK] extends ModuleDef {
  make[DIEffectRunner[F]].from {
    implicit F: Effect[F] => DIEffectRunner.catsAndMonix
  }
  make[DIEffect[F]].from {
    implicit F: Sync[F] => DIEffect.fromCatsEffect
  }
  make[SyncSafe[F]].from {
    implicit F: Sync[F] => SyncSafe.fromSync
  }
  make[DIEffectAsync[F]].from {
    (P0: Parallel[F], T0: Timer[F], C0: Concurrent[F]) =>
      implicit val P: Parallel[F] = P0
      implicit val T: Timer[F] = T0
      implicit val C: Concurrent[F] = C0
      DIEffectAsync[F]
  }
  include(PolymorphicCatsTypeclassesModule[F])
}

object PolymorphicCatsDIEffectModule {
  final def apply[F[_]: TagK]: PolymorphicCatsDIEffectModule[F] = new PolymorphicCatsDIEffectModule[F]
}
