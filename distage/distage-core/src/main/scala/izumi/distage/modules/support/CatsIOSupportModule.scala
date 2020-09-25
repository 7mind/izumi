package izumi.distage.modules.support

import cats.Parallel
import cats.effect.{ConcurrentEffect, ContextShift, ExitCode, IO, IOApp, Timer}
import izumi.distage.model.definition.ModuleDef

object CatsIOSupportModule extends CatsIOSupportModule

/** `cats.effect.IO` effect type support for `distage` resources, effects, roles & tests
  *
  * - Adds [[izumi.distage.model.effect.DIEffect]] instances to support using `cats.effect.IO` in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  * - Adds `cats-effect` typeclass instances for `cats.effect.IO`
  */
trait CatsIOSupportModule extends ModuleDef {
  // DIEffect & cats-effect instances
  include(AnyCatsEffectSupportModule[IO])

  make[ConcurrentEffect[IO]].from(IO.ioConcurrentEffect(_: ContextShift[IO]))
  make[Parallel[IO]].from(IO.ioParallel(_: ContextShift[IO]))

  make[ContextShift[IO]].from((_: PublicIOApp).contextShift)
  make[Timer[IO]].from((_: PublicIOApp).timer)
  make[PublicIOApp]
}

private trait PublicIOApp extends IOApp {
  override def contextShift: ContextShift[IO] = super.contextShift
  override def timer: Timer[IO] = super.timer
  override def run(args: List[String]): IO[ExitCode] = IO.pure(ExitCode(0))
}
