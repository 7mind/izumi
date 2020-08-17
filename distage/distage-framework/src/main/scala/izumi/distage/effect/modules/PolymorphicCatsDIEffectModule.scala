package izumi.distage.effect.modules

import cats.effect.{Async, Bracket, Concurrent, ConcurrentEffect, Sync, Timer}
import cats.{Applicative, Functor, Monad, MonadError, Parallel}
import distage.{ModuleDef, TagK}
import izumi.distage.model.effect.{DIApplicative, DIEffect, DIEffectAsync, DIEffectRunner}
import izumi.functional.mono.SyncSafe
import monix.bio.Task

/**
  * Module definition that binds effect TC instances for an arbitrary F[_].
  *
  */
class PolymorphicCatsDIEffectModule[F[_]: ConcurrentEffect: TagK] extends ModuleDef {
  implicit private def diEffectRunner: DIEffectRunner[F] =
    new DIEffectRunner[F] {
      override def run[A](f: => F[A]): A = ConcurrentEffect[F].toIO(f).unsafeRunSync()
    }

  addImplicit[DIEffectRunner[F]]
  addImplicit[DIApplicative[F]]
  addImplicit[DIEffect[F]]

  make[DIEffectAsync[F]].from {
    (P0: Parallel[F], T0: Timer[F], C0: Concurrent[F]) =>
      implicit val P: Parallel[F] = P0
      implicit val T: Timer[F] = T0
      implicit val C: Concurrent[F] = C0
      DIEffectAsync[F]
  }

  addImplicit[Functor[F]]
  addImplicit[Applicative[F]]
  addImplicit[Monad[F]]
  addImplicit[MonadError[F, Throwable]]
  addImplicit[Bracket[F, Throwable]]
  addImplicit[Sync[F]]
  addImplicit[Async[F]]
  addImplicit[SyncSafe[F]]
}

object PolymorphicCatsDIEffectModule {
  final def apply[F[_]: ConcurrentEffect: TagK]: PolymorphicCatsDIEffectModule[F] =
    new PolymorphicCatsDIEffectModule[F]
}

