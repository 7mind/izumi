package izumi.distage.effect.modules

import cats.effect.{Async, Bracket, ConcurrentEffect, Effect, Sync}
import cats.{Applicative, Functor, Monad, MonadError}
import distage.TagK
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.effect.DIApplicative

class PolymorphicCatsTypeclassesModule[F[_]: TagK] extends ModuleDef {
  make[DIApplicative[F]].from {
    implicit F: Applicative[F] => DIApplicative.fromCats
  }

  make[Functor[F]].using[ConcurrentEffect[F]]
  make[Applicative[F]].using[ConcurrentEffect[F]]
  make[Monad[F]].using[ConcurrentEffect[F]]
  make[ApplicativeError[F, Throwable]].using[ConcurrentEffect[F]]
  make[MonadError[F, Throwable]].using[ConcurrentEffect[F]]
  make[Bracket[F, Throwable]].using[ConcurrentEffect[F]]
  make[Sync[F]].using[ConcurrentEffect[F]]
  make[Async[F]].using[ConcurrentEffect[F]]
  make[LiftIO[F]].using[ConcurrentEffect[F]]
  make[Effect[F]].using[ConcurrentEffect[F]]
  make[Concurrent[F]].using[ConcurrentEffect[F]]
}

object PolymorphicCatsTypeclassesModule {
  final def apply[F[_]: TagK]: PolymorphicCatsTypeclassesModule[F] = new PolymorphicCatsTypeclassesModule[F]
}
