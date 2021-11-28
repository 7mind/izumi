package izumi.distage.modules.support

import cats.Parallel
import cats.effect.{ConcurrentEffect, ExitCode, IO, IOApp}
import izumi.distage.model.definition.ModuleDef
import izumi.distage.modules.platform.CatsIOPlatformDependentSupportModule

import scala.concurrent.ExecutionContext
import cats.effect.Temporal

object CatsIOSupportModule extends CatsIOSupportModule

/**
  * `cats.effect.IO` effect type support for `distage` resources, effects, roles & tests
  *
  *  - Adds [[izumi.distage.model.effect.QuasiIO]] instances to support using `cats.effect.IO` in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  *  - Adds `cats-effect` typeclass instances for `cats.effect.IO`
  *
  * Will also add the following components:
  *   - [[cats.effect.Blocker]] by using [[cats.effect.Blocker.apply]]
  *
  * Added into scope by [[izumi.distage.modules.DefaultModule]].
  *
  * Bindings to the same keys in your own [[izumi.distage.model.definition.ModuleDef]] or plugins will override these defaults.
  */
trait CatsIOSupportModule extends ModuleDef with CatsIOPlatformDependentSupportModule {
  // QuasiIO & cats-effect instances
  include(AnyCatsEffectSupportModule[IO])

  make[ConcurrentEffect[IO]].from(IO.ioConcurrentEffect(_: ContextShift[IO]))
  make[Parallel[IO]].from(IO.ioParallel(_: ContextShift[IO]))

  make[ContextShift[IO]].from((_: PublicIOApp).contextShift)
  make[Temporal[IO]].from((_: PublicIOApp).timer)
  make[ExecutionContext].named("cpu").from((_: PublicIOApp).executionContext)
  make[PublicIOApp]
}

private[support] trait PublicIOApp extends IOApp {
  override def contextShift: ContextShift[IO] = super.contextShift
  override def timer: Temporal[IO] = super.timer
  override def executionContext: ExecutionContext = super.executionContext
  override def run(args: List[String]): IO[ExitCode] = IO.pure(ExitCode(0))
}
