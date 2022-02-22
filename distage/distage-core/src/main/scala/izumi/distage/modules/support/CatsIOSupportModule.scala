package izumi.distage.modules.support

import cats.Parallel
import cats.effect.kernel.Async
import cats.effect.IO
import cats.effect.unsafe.{IORuntime, IORuntimeConfig}
import izumi.distage.model.definition.ModuleDef
import izumi.distage.modules.platform.CatsIOPlatformDependentSupportModule

import scala.concurrent.ExecutionContext

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

  make[Async[IO]].from(IO.asyncForIO)
  make[Parallel[IO]].from(IO.parallelForIO)

  make[IORuntimeConfig].fromValue(IORuntimeConfig())
  make[IORuntime].from(IORuntime.apply _)
  make[ExecutionContext].named("cpu").from((_: IORuntime).compute)
}
