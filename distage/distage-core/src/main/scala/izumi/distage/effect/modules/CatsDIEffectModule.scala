package izumi.distage.effect.modules

import cats.Parallel
import cats.effect.{ConcurrentEffect, ContextShift, ExitCode, IO, IOApp, Timer}
import izumi.distage.model.definition.ModuleDef

object CatsDIEffectModule extends CatsDIEffectModule

/** `cats.effect.IO` effect type support for `distage` resources, effects, roles & tests
  *
  * - Adds [[cats.effect]] typeclass instances for `cats-effect`
  * - Adds [[izumi.distage.model.effect.DIEffect]] instances to support using `cats-effect` in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  */
trait CatsDIEffectModule extends ModuleDef {
  // DIEffect & cats-effect instances
  include(PolymorphicCatsDIEffectModule[IO])

  make[ConcurrentEffect[IO]].from(IO.ioConcurrentEffect(_: ContextShift[IO]))
  make[Parallel[IO]].from(IO.ioParallel(_: ContextShift[IO]))

  make[ContextShift[IO]].from((_: PublicIOApp).contextShift)
  make[Timer[IO]].from((_: PublicIOApp).timer)
  make[PublicIOApp]
}

trait PublicIOApp extends IOApp {
  override def contextShift: ContextShift[IO] = super.contextShift
  override def timer: Timer[IO] = super.timer
  override def run(args: List[String]): IO[ExitCode] = IO.pure(ExitCode(0))
}
