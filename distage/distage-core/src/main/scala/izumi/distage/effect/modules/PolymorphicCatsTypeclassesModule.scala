package izumi.distage.effect.modules

import cats.effect.{Async, Bracket, Concurrent, ConcurrentEffect, ContextShift, Effect, LiftIO, Sync, Timer}
import cats.{Applicative, ApplicativeError, Apply, FlatMap, Functor, Invariant, InvariantSemigroupal, Monad, MonadError, Parallel, Semigroupal}
import distage.TagK
import izumi.distage.model.definition.ModuleDef

/**
  * Adds `cats-effect` typeclass instances for any effect type `F[_]` with an available `make[ConcurrentEffect[F]` binding
  *
  * Depends on `make[ConcurrentEffect[F]]`.
  */
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
  @inline def apply[F[_]: TagK]: PolymorphicCatsTypeclassesModule[F] = new PolymorphicCatsTypeclassesModule[F]

  /**
    * Make [[PolymorphicCatsTypeclassesModule]], binding the required dependencies in place to values from implicit scope
    *
    * `make[Parallel[F]]`, `make[Timer[F]]` and `make[ContextShift[F]]` are not required by [[PolymorphicCatsTypeclassesModule]]
    * but are added for completeness
    */
  def withImplicits[F[_]: TagK: ConcurrentEffect: Parallel: Timer: ContextShift]: ModuleDef = new ModuleDef {
    addImplicit[ConcurrentEffect[F]]
    addImplicit[Parallel[F]]
    addImplicit[Timer[F]]
    addImplicit[ContextShift[F]]

    include(PolymorphicCatsTypeclassesModule[F])
  }
}
