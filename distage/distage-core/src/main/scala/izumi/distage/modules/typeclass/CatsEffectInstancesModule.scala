package izumi.distage.modules.typeclass

import cats.effect.kernel.*
import cats.{Applicative, ApplicativeError, Apply, FlatMap, Functor, Invariant, InvariantSemigroupal, Monad, MonadError, Parallel, Semigroupal}
import distage.TagK
import izumi.distage.model.definition.ModuleDef

/**
  * Adds `cats-effect` typeclass instances for any effect type `F[_]` with an available `make[ConcurrentEffect[F]` binding
  *
  * Depends on `ConcurrentEffect[F]`.
  */
class CatsEffectInstancesModule[F[_]: TagK] extends ModuleDef {
  make[Invariant[F]].using[Async[F]]
  make[Semigroupal[F]].using[Async[F]]
  make[InvariantSemigroupal[F]].using[Async[F]]

  make[Functor[F]].using[Async[F]]
  make[Apply[F]].using[Async[F]]
  make[Applicative[F]].using[Async[F]]
  make[FlatMap[F]].using[Async[F]]
  make[Monad[F]].using[Async[F]]
  make[ApplicativeError[F, Throwable]].using[Async[F]]
  make[MonadError[F, Throwable]].using[Async[F]]

  make[Unique[F]].using[Async[F]]
  make[Clock[F]].using[Async[F]]
  make[MonadCancel[F, Throwable]].using[Async[F]]
  make[GenSpawn[F, Throwable]].using[Async[F]]
  make[GenConcurrent[F, Throwable]].using[Async[F]]
  make[GenTemporal[F, Throwable]].using[Async[F]]
  make[Sync[F]].using[Async[F]]
}

object CatsEffectInstancesModule {
  @inline def apply[F[_]: TagK]: CatsEffectInstancesModule[F] = new CatsEffectInstancesModule[F]

  /**
    * Make [[CatsEffectInstancesModule]], binding the required dependencies in place to values from implicit scope
    *
    * `make[Parallel[F]]`, `make[Timer[F]]` and `make[ContextShift[F]]` are not required by [[CatsEffectInstancesModule]]
    * but are added for completeness
    */
  def withImplicits[F[_]: TagK: Async: Parallel]: ModuleDef = new ModuleDef {
    addImplicit[Async[F]]
    addImplicit[Parallel[F]]

    include(CatsEffectInstancesModule[F])
  }
}
