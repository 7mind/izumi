package izumi.distage.modules.platform

import izumi.distage.model.definition.{Id, Lifecycle, ModuleDef}
import izumi.functional.bio.UnsafeRun2.{FailureHandler, ZIORunner}
import izumi.functional.bio.{BlockingIO2, BlockingIO3, BlockingIOInstances, UnsafeRun2}
import izumi.reflect.Tag
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
import zio.internal.tracing.TracingConfig
import zio.internal.{Executor, Platform}
import zio.random.Random
import zio.system.System
import zio.{Has, Runtime, ZEnv, ZIO}

import java.util.concurrent.{Executors, ThreadPoolExecutor}
import scala.concurrent.ExecutionContext

private[modules] abstract class ZIOPlatformDependentSupportModule[R: Tag] extends ModuleDef {
  make[ZEnv].from(Has.allOf(_: Clock.Service, _: Console.Service, _: System.Service, _: Random.Service, _: Blocking.Service))

  make[UnsafeRun2[ZIO[R, _, _]]].using[ZIORunner[R]]

  make[BlockingIO3[ZIO]].from(BlockingIOInstances.BlockingZIOFromBlocking(_: zio.blocking.Blocking.Service))
  make[BlockingIO2[ZIO[R, +_, +_]]].from {
    implicit B: BlockingIO3[ZIO] => BlockingIO2[ZIO[R, +_, +_]]
  }

  make[zio.blocking.Blocking].from(Has(_: Blocking.Service))
  make[zio.blocking.Blocking.Service].from {
    (blockingPool: ThreadPoolExecutor @Id("io")) =>
      new Blocking.Service {
        override val blockingExecutor: Executor = Executor.fromThreadPoolExecutor(_ => Int.MaxValue)(blockingPool)
      }
  }

  make[ZIORunner[R]].from {
    (cpuPool: ThreadPoolExecutor @Id("cpu"), handler: FailureHandler, tracingConfig: TracingConfig, initialEnv: R @Id("zio-initial-env")) =>
      UnsafeRun2.createZIO(
        cpuPool = cpuPool,
        handler = handler,
        tracingConfig = tracingConfig,
        initialEnv = initialEnv,
      )
  }
  make[TracingConfig].fromValue(TracingConfig.enabled)
  make[FailureHandler].fromValue(FailureHandler.Default)
  make[Runtime[R]].from((_: ZIORunner[R]).runtime)
  make[Platform].from((_: Runtime[R]).platform)

  make[ThreadPoolExecutor].named("cpu").fromResource {
    () =>
      val coresOr2 = java.lang.Runtime.getRuntime.availableProcessors() max 2
      Lifecycle.fromExecutorService(Executors.newFixedThreadPool(coresOr2).asInstanceOf[ThreadPoolExecutor])
  }
  make[ThreadPoolExecutor].named("io").fromResource {
    () =>
      Lifecycle.fromExecutorService(Executors.newCachedThreadPool().asInstanceOf[ThreadPoolExecutor])
  }

  make[ExecutionContext].named("cpu").from(ExecutionContext.fromExecutor(_: ThreadPoolExecutor @Id("cpu")))
  make[ExecutionContext].named("io").from(ExecutionContext.fromExecutor(_: ThreadPoolExecutor @Id("io")))
}
