package izumi.distage.modules.typeclass

import cats.effect.kernel.*
import cats.{Applicative, ApplicativeError, Apply, FlatMap, Functor, Invariant, InvariantSemigroupal, Monad, MonadError, Parallel, Semigroupal}
import izumi.distage.model.definition.ModuleDef
import izumi.reflect.TagK

object CatsEffectInstancesModule {

  /**
    * Adds `cats-effect` typeclass instances for any effect type `F[_]` with an available `make[Async[F]]` binding
    *
    * Depends on `make[Async[F]]`.
    */
  def usingAsync[F[_]: TagK]: ModuleDef = new ModuleDef {
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

  /**
    * Make [[CatsEffectInstancesModule.usingAsync]], binding the required dependencies in place to values from implicit scope
    *
    * `make[Parallel[F]]` is not required by [[CatsEffectInstancesModule.usingAsync]] but added for completeness
    */
  def usingImplicits[F[_]: TagK: Async: Parallel]: ModuleDef = new ModuleDef {
    include(CatsEffectInstancesModule.usingAsync[F])

    addImplicit[Async[F]]
    addImplicit[Parallel[F]]
  }

  @deprecated("renamed to usingAsync", "1.3")
  @inline def apply[F[_]: TagK]: ModuleDef = usingAsync[F]
}
