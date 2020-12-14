package izumi.distage.modules.platform

import java.util.concurrent.{Executors, ThreadPoolExecutor}

import distage.Id
import izumi.distage.model.definition.{Lifecycle, ModuleDef}
import izumi.functional.bio.UnsafeRun2.{FailureHandler, ZIORunner}
import izumi.functional.bio.{BlockingIO2, BlockingIO3, BlockingIOInstances, UnsafeRun2, UnsafeRun3}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
import zio.internal.tracing.TracingConfig
import zio.internal.{Executor, Platform}
import zio.random.Random
import zio.system.System
import zio.{Has, IO, Runtime, ZEnv, ZIO}

import scala.concurrent.ExecutionContext

private[modules] trait ZIOPlatformDependentSupportModule extends ModuleDef {
  make[ZEnv].from(Has.allOf(_: Clock.Service, _: Console.Service, _: System.Service, _: Random.Service, _: Blocking.Service))

  make[UnsafeRun3[ZIO]].using[ZIORunner]

  make[BlockingIO3[ZIO]].from(BlockingIOInstances.BlockingZIO3FromBlocking(_: zio.blocking.Blocking.Service))
  make[BlockingIO2[IO]].from {
    implicit B: BlockingIO3[ZIO] => BlockingIO2[IO]
  }

  make[zio.blocking.Blocking].from(Has(_: Blocking.Service))
  make[zio.blocking.Blocking.Service].from {
    blockingPool: ThreadPoolExecutor @Id("zio.io") =>
      new Blocking.Service {
        override val blockingExecutor: Executor = Executor.fromThreadPoolExecutor(_ => Int.MaxValue)(blockingPool)
      }
  }

  make[ZIORunner].from {
    (cpuPool: ThreadPoolExecutor @Id("zio.cpu"), handler: FailureHandler, tracingConfig: TracingConfig) =>
      UnsafeRun2.createZIO(
        cpuPool = cpuPool,
        handler = handler,
        tracingConfig = tracingConfig,
      )
  }
  make[TracingConfig].fromValue(TracingConfig.enabled)
  make[FailureHandler].fromValue(FailureHandler.Default)
  make[Runtime[Any]].from((_: ZIORunner).runtime)
  make[Platform].from((_: Runtime[Any]).platform)

  make[ThreadPoolExecutor].named("zio.cpu").fromResource {
    () =>
      val coresOr2 = java.lang.Runtime.getRuntime.availableProcessors() max 2
      Lifecycle.fromExecutorService(Executors.newFixedThreadPool(coresOr2).asInstanceOf[ThreadPoolExecutor])
  }
  make[ThreadPoolExecutor].named("zio.io").fromResource {
    () =>
      Lifecycle.fromExecutorService(Executors.newCachedThreadPool().asInstanceOf[ThreadPoolExecutor])
  }

  make[ExecutionContext].named("zio.cpu").from(ExecutionContext.fromExecutor(_: ThreadPoolExecutor @Id("zio.cpu")))
  make[ExecutionContext].named("zio.io").from(ExecutionContext.fromExecutor(_: ThreadPoolExecutor @Id("zio.io")))
}
