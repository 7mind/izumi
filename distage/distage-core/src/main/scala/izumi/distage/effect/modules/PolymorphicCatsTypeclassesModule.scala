package izumi.distage.effect.modules

import cats.effect.{Async, Bracket, Concurrent, ConcurrentEffect, Effect, LiftIO, Sync}
import cats.{Applicative, ApplicativeError, Apply, FlatMap, Functor, Invariant, InvariantSemigroupal, Monad, MonadError, Semigroupal}
import distage.TagK
import izumi.distage.model.definition.ModuleDef

class PolymorphicCatsTypeclassesModule[F[_]: TagK] extends ModuleDef {
  make[Invariant[F]].using[ConcurrentEffect[F]]
  make[Semigroupal[F]].using[ConcurrentEffect[F]]
  make[InvariantSemigroupal[F]].using[ConcurrentEffect[F]]

  make[Functor[F]].using[ConcurrentEffect[F]]
  make[Apply[F]].using[ConcurrentEffect[F]]
  make[Applicative[F]].using[ConcurrentEffect[F]]
  make[FlatMap[F]].using[ConcurrentEffect[F]]
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
  def apply[F[_]: TagK]: PolymorphicCatsTypeclassesModule[F] = new PolymorphicCatsTypeclassesModule[F]
}
