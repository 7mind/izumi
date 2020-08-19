package izumi.distage.effect.modules

import cats.Parallel
import cats.effect.{Concurrent, ConcurrentEffect, ContextShift, ExitCode, IO, IOApp, Timer}
import izumi.distage.model.definition.ModuleDef

object CatsDIEffectModule extends CatsDIEffectModule

trait CatsDIEffectModule extends ModuleDef {
  make[Parallel[IO]].from(IO.ioParallel(_: ContextShift[IO]))
  make[ConcurrentEffect[IO]]
    .from(IO.ioConcurrentEffect(_: ContextShift[IO]))
    .aliased[Concurrent[IO]]

  make[ContextShift[IO]].from((_: PublicIOApp).contextShift)
  make[Timer[IO]].from((_: PublicIOApp).timer)

  make[PublicIOApp].from {
    new PublicIOApp { override def run(args: List[String]): IO[ExitCode] = IO.pure(ExitCode(0)) }
  }

  include(PolymorphicCatsDIEffectModule[IO])
}

trait PublicIOApp extends IOApp {
  override def contextShift: ContextShift[IO] = super.contextShift
  override def timer: Timer[IO] = super.timer
}
