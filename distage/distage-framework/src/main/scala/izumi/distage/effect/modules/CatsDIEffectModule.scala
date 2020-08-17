package izumi.distage.effect.modules

import cats.Parallel
import cats.effect.{Concurrent, ConcurrentEffect, ContextShift, IO, IOApp, Timer}
import izumi.distage.model.definition.ModuleDef

import scala.concurrent.ExecutionContext

object CatsDIEffectModule extends CatsDIEffectModule

trait CatsDIEffectModule extends ModuleDef {
  make[Parallel[IO]].from(IO.ioParallel(_: ContextShift[IO]))
  make[ConcurrentEffect[IO]]
    .from(IO.ioConcurrentEffect(_: ContextShift[IO]))
    .aliased[Concurrent[IO]]

  make[ContextShift[IO]].from((_: PublicIOApp).contextShift)
  make[Timer[IO]].from((_: PublicIOApp).timer)

  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  include(PolymorphicCatsDIEffectModule.apply[IO])
}

trait PublicIOApp extends IOApp {
  override def contextShift: ContextShift[IO] = super.contextShift
  override def timer: Timer[IO] = super.timer
}
