package izumi.distage.effect.modules

import cats.effect.{Async, Bracket, Concurrent, ConcurrentEffect, Effect, Sync, Timer}
import cats.{Applicative, Functor, Monad, MonadError, Parallel}
import distage.{ModuleDef, TagK}
import izumi.distage.model.effect.{DIApplicative, DIEffect, DIEffectAsync, DIEffectRunner}
import izumi.functional.mono.SyncSafe

/**
  * Module definition that binds effect TC instances for an arbitrary F[_].
  */
class PolymorphicCatsDIEffectModule[F[_]: TagK] extends ModuleDef {
  make[DIEffectRunner[F]].from {
    implicit F: Effect[F] => DIEffectRunner.catsAndMonix
  }

  make[DIApplicative[F]].from {
    implicit F: Applicative[F] => DIApplicative.fromCats
  }

  make[DIEffect[F]].from {
    implicit F: Sync[F] => DIEffect.fromCatsEffect
  }

  make[DIEffectAsync[F]].from {
    (P0: Parallel[F], T0: Timer[F], C0: Concurrent[F]) =>
      implicit val P: Parallel[F] = P0
      implicit val T: Timer[F] = T0
      implicit val C: Concurrent[F] = C0
      DIEffectAsync[F]
  }

  make[Functor[F]].using[ConcurrentEffect[F]]
  make[Applicative[F]].using[ConcurrentEffect[F]]
  make[Monad[F]].using[ConcurrentEffect[F]]
  make[MonadError[F, Throwable]].using[ConcurrentEffect[F]]
  make[Bracket[F, Throwable]].using[ConcurrentEffect[F]]
  make[Sync[F]].using[ConcurrentEffect[F]]
  make[Async[F]].using[ConcurrentEffect[F]]
  make[SyncSafe[F]].from {
    implicit F: Sync[F] => SyncSafe.fromSync
  }
  make[Effect[F]].using[ConcurrentEffect[F]]
}

object PolymorphicCatsDIEffectModule {
  final def apply[F[_]: TagK]: PolymorphicCatsDIEffectModule[F] = new PolymorphicCatsDIEffectModule[F]
}
