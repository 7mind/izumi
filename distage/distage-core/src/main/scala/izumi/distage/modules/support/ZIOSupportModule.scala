package izumi.distage.modules.support

import izumi.distage.model.definition.ModuleDef
import izumi.distage.modules.platform.ZIOPlatformDependentSupportModule
import izumi.functional.bio._
import zio.{Has, IO, ZEnv, ZIO}

object ZIOSupportModule extends ZIOSupportModule

/**
  * `zio.ZIO` effect type support for `distage` resources, effects, roles & tests
  *
  *  - Adds [[izumi.distage.model.effect.QuasiIO]] instances to support using ZIO in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  *  - Adds [[izumi.functional.bio]] typeclass instances for ZIO
  *
  * Will also add the following components:
  *   - `ThreadPoolExecutor @Id("zio.cpu")` for CPU-bound tasks (will be used for all tasks by default by [[zio.Runtime]])
  *   - `ThreadPoolExecutor @Id("zio.io")` and blocking IO tasks (tasks can be scheduled to it via [[izumi.functional.bio.BlockingIO]] or [[zio.blocking.blocking]])
  *   - `ExecutionContext @Id("zio.cpu")` & `ExecutionContext @Id("zio.io")` respectively
  *   - [[zio.internal.tracing.TracingConfig]] will be set to [[zio.internal.tracing.TracingConfig.enabled]] by default
  *   - Standard ZIO services: [[zio.console.Console]], [[zio.clock.Clock]], [[zio.system.System]], [[zio.random.Random]] and corresponding `.Service` types
  *
  * Added into scope by [[izumi.distage.modules.DefaultModule]].
  * Bindings to the same keys in your own [[izumi.distage.model.definition.ModuleDef]] or plugins will override these defaults.
  */
trait ZIOSupportModule extends ModuleDef with ZIOPlatformDependentSupportModule {
  include(AnyBIO3SupportModule[ZIO])

  addImplicit[Async3[ZIO]]
  make[Temporal3[ZIO]].from {
    implicit r: zio.clock.Clock =>
      implicitly[Temporal3[ZIO]]
  }
  addImplicit[Local3[ZIO]]
  addImplicit[Fork3[ZIO]]
  addImplicit[Primitives3[ZIO]]

  addImplicit[TransZio[IO]]

  make[zio.Runtime[ZEnv]].from((r: zio.Runtime[Any], zenv: ZEnv) => r.map(_ => zenv))

  make[zio.clock.Clock].from(Has(_: zio.clock.Clock.Service))
  make[zio.clock.Clock.Service].from(zio.clock.Clock.Service.live)

  make[zio.console.Console].from(Has(_: zio.console.Console.Service))
  make[zio.console.Console.Service].from(zio.console.Console.Service.live)

  make[zio.system.System].from(Has(_: zio.system.System.Service))
  make[zio.system.System.Service].from(zio.system.System.Service.live)

  make[zio.random.Random].from(Has(_: zio.random.Random.Service))
  make[zio.random.Random.Service].from(zio.random.Random.Service.live)
}
