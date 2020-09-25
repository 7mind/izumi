package izumi.distage.effect.modules

import java.util.concurrent.{Executors, ThreadPoolExecutor}

import distage.Id
import izumi.distage.model.definition.{DIResource, ModuleDef}
import izumi.functional.bio.BIORunner.{FailureHandler, ZIORunner}
import izumi.functional.bio._
import zio.blocking.Blocking
import zio.internal.Executor
import zio.internal.tracing.TracingConfig
import zio.{Has, IO, Runtime, ZIO}

import scala.concurrent.ExecutionContext

object ZIODIEffectModule extends ZIODIEffectModule

/** `zio.ZIO` effect type support for `distage` resources, effects, roles & tests
  *
  * - Adds [[izumi.distage.model.effect.DIEffect]] instances to support using ZIO in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  * - Adds [[izumi.functional.bio]] typeclass instances for ZIO
  *
  * Note: by default this module will add the following components:
  *   - `ThreadPoolExecutor @Id("zio.cpu")` for CPU-bound tasks (used by default in [[zio.Runtime]])
  *   - `ThreadPoolExecutor @Id("zio.io")` and blocking IO tasks (designated via [[izumi.functional.bio.BlockingIO]] or [[zio.blocking.blocking]])
  *   - [[scala.concurrent.ExecutionContext]] bindings with the same `@Id`
  *   - [[zio.internal.tracing.TracingConfig]] will be set to [[zio.internal.tracing.TracingConfig.enabled]] by default
  *
  * Bindings to the same keys in your own [[izumi.distage.model.definition.ModuleDef]] or plugins will override these defaults.
  */
trait ZIODIEffectModule extends ModuleDef {
  include(PolymorphicBIO3DIEffectModule[ZIO])

  addImplicit[BIOAsync3[ZIO]]
  make[BIOTemporal3[ZIO]].from {
    implicit r: zio.clock.Clock =>
      implicitly[BIOTemporal3[ZIO]]
  }
  addImplicit[BIOLocal[ZIO]]
  addImplicit[BIOFork3[ZIO]]
  addImplicit[BIOPrimitives3[ZIO]]
  make[BlockingIO3[ZIO]].from(BlockingIOInstances.blockingIOZIO3Blocking(_: zio.blocking.Blocking))
  make[BlockingIO[IO]].from { implicit B: BlockingIO3[ZIO] => BlockingIO[IO] }

  addImplicit[BIOTransZio[IO]]

  make[BIORunner3[ZIO]].using[ZIORunner]

  make[zio.blocking.Blocking].from(Has(_: Blocking.Service))
  make[zio.clock.Clock].from(Has(_: zio.clock.Clock.Service))

  make[zio.blocking.Blocking.Service].from {
    blockingPool: ThreadPoolExecutor @Id("zio.io") =>
      new Blocking.Service {
        override val blockingExecutor: Executor = Executor.fromThreadPoolExecutor(_ => Int.MaxValue)(blockingPool)
      }
  }
  make[zio.clock.Clock.Service].from(zio.clock.Clock.Service.live)

  make[ZIORunner].from {
    (cpuPool: ThreadPoolExecutor @Id("zio.cpu"), handler: FailureHandler, tracingConfig: TracingConfig) =>
      BIORunner.createZIO(
        cpuPool = cpuPool,
        handler = handler,
        tracingConfig = tracingConfig,
      )
  }
  make[TracingConfig].fromValue(TracingConfig.enabled)
  make[FailureHandler].fromValue(FailureHandler.Default)
  make[Runtime[Any]].from((_: ZIORunner).runtime)

  make[ThreadPoolExecutor].named("zio.cpu").fromResource {
    () =>
      val coresOr2 = java.lang.Runtime.getRuntime.availableProcessors() max 2
      DIResource.fromExecutorService(Executors.newFixedThreadPool(coresOr2).asInstanceOf[ThreadPoolExecutor])
  }
  make[ThreadPoolExecutor].named("zio.io").fromResource {
    () =>
      DIResource.fromExecutorService(Executors.newCachedThreadPool().asInstanceOf[ThreadPoolExecutor])
  }

  make[ExecutionContext].named("zio.cpu").from(ExecutionContext.fromExecutor(_: ThreadPoolExecutor @Id("zio.cpu")))
  make[ExecutionContext].named("zio.io").from(ExecutionContext.fromExecutor(_: ThreadPoolExecutor @Id("zio.io")))
}
