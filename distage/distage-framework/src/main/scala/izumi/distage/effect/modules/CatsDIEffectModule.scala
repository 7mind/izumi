package izumi.distage.effect.modules

import cats.effect.{Async, Bracket, Concurrent, ConcurrentEffect, ContextShift, Effect, ExitCode, IO, IOApp, Sync, Timer}
import cats.{Applicative, Functor, Monad, MonadError, Parallel}
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.effect.{DIEffect, DIEffectAsync, DIEffectRunner}

object CatsDIEffectModule extends CatsDIEffectModule

trait CatsDIEffectModule extends ModuleDef {
  addImplicit[DIEffectRunner[IO]]
  addImplicit[DIEffect[IO]]

  make[DIEffectAsync[IO]].from {
    (P0: Parallel[IO], T0: Timer[IO]) =>
      implicit val P: Parallel[IO] = P0
      implicit val T: Timer[IO] = T0
      DIEffectAsync[IO]
  }

  addImplicit[Functor[IO]]
  addImplicit[Applicative[IO]]
  addImplicit[Monad[IO]]
  addImplicit[MonadError[IO, Throwable]]
  addImplicit[Bracket[IO, Throwable]]
  addImplicit[Sync[IO]]
  addImplicit[Async[IO]]
  addImplicit[Effect[IO]]

  make[Parallel[IO]].from(IO.ioParallel(_: ContextShift[IO]))
  make[ConcurrentEffect[IO]]
    .from(IO.ioConcurrentEffect(_: ContextShift[IO]))
    .aliased[Concurrent[IO]]

  make[ContextShift[IO]].from((_: PublicIOApp).contextShift)
  make[Timer[IO]].from((_: PublicIOApp).timer)

  make[PublicIOApp].from {
    new PublicIOApp { override def run(args: List[String]): IO[ExitCode] = IO.pure(ExitCode(0)) }
  }
}

// extract default ContextShift & Timer from IOApp
trait PublicIOApp extends IOApp {
  override def contextShift: ContextShift[IO] = super.contextShift
  override def timer: Timer[IO] = super.timer
}
