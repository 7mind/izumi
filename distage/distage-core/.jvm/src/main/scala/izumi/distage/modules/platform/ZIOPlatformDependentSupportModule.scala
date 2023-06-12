package izumi.distage.modules.platform

import izumi.distage.model.definition.{Id, ModuleDef}
import izumi.functional.bio.UnsafeRun2.{FailureHandler, ZIORunner}
import izumi.functional.bio.{BlockingIO2, BlockingIO3, BlockingIOInstances, UnsafeRun2}
import izumi.reflect.Tag
import zio.{Executor, Runtime, ZEnvironment, ZIO, ZLayer}

import scala.concurrent.ExecutionContext

private[modules] abstract class ZIOPlatformDependentSupportModule[R: Tag] extends ModuleDef {
  make[UnsafeRun2[ZIO[R, _, _]]].using[ZIORunner[R]]

  make[BlockingIO3[ZIO]].from(BlockingIOInstances.BlockingZIODefault)
  make[BlockingIO2[ZIO[R, +_, +_]]].from {
    implicit B: BlockingIO3[ZIO] => BlockingIO2[ZIO[R, +_, +_]]
  }

  make[ZIORunner[R]].from {
    (
      cpuPool: Executor @Id("cpu"),
      blockingPool: Executor @Id("io"),
      handler: FailureHandler,
      runtimeConfiguration: List[ZLayer[Any, Nothing, Any]] @Id("zio-runtime-configuration"),
      initialEnv: ZEnvironment[R] @Id("zio-initial-env"),
    ) =>
      UnsafeRun2.createZIO(
        customCpuPool = Some(cpuPool),
        customBlockingPool = Some(blockingPool),
        handler = handler,
        otherRuntimeConfiguration = runtimeConfiguration,
        initialEnv = initialEnv,
      )
  }
  make[FailureHandler].fromValue(FailureHandler.Default)
  make[List[ZLayer[Any, Nothing, Any]]].named("zio-runtime-configuration").fromValue(Nil)

  make[Executor].named("cpu").from {
    Runtime.defaultExecutor // ZIO executor seems to be optimized for globality, for better or worse
//    Executor.makeDefault()
  }
  make[Executor].named("io").from {
    // no reason to use custom blocking pool, since this one is hardcoded in zio.internal.ZScheduler.submitBlocking
    Runtime.defaultBlockingExecutor
  }

  make[ExecutionContext].named("cpu").from((_: Executor @Id("cpu")).asExecutionContext)
  make[ExecutionContext].named("io").from((_: Executor @Id("io")).asExecutionContext)
}
