package izumi.distage.modules.support

import izumi.distage.modules.platform.ZIOPlatformDependentSupportModule
import izumi.functional.bio.*
import izumi.functional.bio.retry.Scheduler3
import izumi.reflect.Tag
import zio.{Has, IO, ZEnv, ZIO}

object ZIOSupportModule {
  def apply[R: Tag]: ZIOSupportModule[R] = new ZIOSupportModule[R]
}

/**
  * `zio.ZIO` effect type support for `distage` resources, effects, roles & tests
  *
  *  - Adds [[izumi.functional.quasi.QuasiIO]] instances to support using ZIO in `Injector`, `distage-framework` & `distage-testkit-scalatest`
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
  * If [[https://github.com/zio/interop-cats/ interop-cats]] library is on the classpath during compilation,
  * implicit [[izumi.distage.modules.DefaultModule.forZIOPlusCats]] will be picked up instead of [[izumi.distage.modules.DefaultModule.forZIO]]
  * and will add a module with `cats-effect` instances [[izumi.distage.modules.typeclass.ZIOCatsEffectInstancesModule]]
  *
  * Bindings to the same keys in your own [[izumi.distage.model.definition.ModuleDef]] or plugins will override these defaults.
  */
class ZIOSupportModule[R: Tag] extends ZIOPlatformDependentSupportModule[R] {
  include(AnyBIO3SupportModule[ZIO, R])

  make[Any].named("zio-initial-env").fromValue(()) // assume default environment is `Any` or `ZEnv`, otherwise let the error message guide the user here.
  make[ZEnv].named("zio-initial-env").using[ZEnv]

  addImplicit[Async3[ZIO]]
  make[Temporal3[ZIO]].from {
    implicit r: zio.clock.Clock =>
      implicitly[Temporal3[ZIO]]
  }
  addImplicit[Local3[ZIO]]
  addImplicit[Fork3[ZIO]]
  addImplicit[Primitives3[ZIO]]
  addImplicit[PrimitivesM3[ZIO]]

  make[Scheduler3[ZIO]].from {
    implicit r: Temporal3[ZIO] =>
      implicitly[Scheduler3[ZIO]]
  }

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
