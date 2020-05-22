package izumi.distage.effect.modules

import java.util.concurrent.{Executors, ThreadPoolExecutor}

import distage.Id
import izumi.distage.model.definition.{DIResource, ModuleDef}
import izumi.distage.model.effect._
import izumi.functional.bio.BIORunner.{FailureHandler, ZIORunner}
import izumi.functional.bio._
import izumi.logstage.api.IzLogger
import zio.blocking.Blocking
import zio.internal.Executor
import zio.internal.tracing.TracingConfig
import zio.{Has, IO, ZIO}

import scala.concurrent.ExecutionContext

object ZIODIEffectModule extends ZIODIEffectModule

trait ZIODIEffectModule extends ModuleDef {
  make[DIEffectRunner2[IO]].from[DIEffectRunner.BIOImpl[IO]]
  addImplicit[DIApplicative2[IO]]
  addImplicit[DIEffect2[IO]]
  make[DIEffectAsync2[IO]].from(DIEffectAsync.fromBIOTemporal(_: BIOTemporal[IO]))

  make[ExecutionContext].named("zio.cpu").from(ExecutionContext.fromExecutor(_: ThreadPoolExecutor @Id("zio.cpu")))
  make[ExecutionContext].named("zio.io").from(ExecutionContext.fromExecutor(_: ThreadPoolExecutor @Id("zio.io")))
  make[ThreadPoolExecutor].named("zio.cpu").fromResource {
    () =>
      val coresOr2 = Runtime.getRuntime.availableProcessors() max 2
      DIResource.fromExecutorService(Executors.newFixedThreadPool(coresOr2).asInstanceOf[ThreadPoolExecutor])
  }
  make[ThreadPoolExecutor].named("zio.io").fromResource {
    () =>
      DIResource.fromExecutorService(Executors.newCachedThreadPool().asInstanceOf[ThreadPoolExecutor])
  }

  make[zio.blocking.Blocking.Service].from {
    blockingPool: ThreadPoolExecutor @Id("zio.io") =>
      new Blocking.Service {
        override val blockingExecutor: Executor = Executor.fromThreadPoolExecutor(_ => Int.MaxValue)(blockingPool)
      }
  }
  make[zio.blocking.Blocking].from(Has(_: Blocking.Service))
  make[BlockingIO3[ZIO]].from(BlockingIOInstances.blockingIOZIO3Blocking(_: zio.blocking.Blocking))
  make[BlockingIO[IO]].from(BlockingIOInstances.blockingIO3To2[ZIO, Any](_: BlockingIO3[ZIO]))

  addImplicit[BIOTransZio[IO]]
  addImplicit[BIOFork3[ZIO]]
  addImplicit[BIOFork[IO]]
  addImplicit[SyncSafe3[ZIO]]
  addImplicit[BIOPrimitives3[ZIO]]
  addImplicit[BIOPrimitives[IO]]

  addImplicit[BIOFunctor3[ZIO]]
  addImplicit[BIOBifunctor3[ZIO]]
  addImplicit[BIOApplicative3[ZIO]]
  addImplicit[BIOGuarantee3[ZIO]]
  addImplicit[BIOError3[ZIO]]
  addImplicit[BIOMonad3[ZIO]]
  addImplicit[BIOMonadError3[ZIO]]
  addImplicit[BIOBracket3[ZIO]]
  addImplicit[BIOPanic3[ZIO]]
  addImplicit[BIO3[ZIO]]
  addImplicit[BIOParallel3[ZIO]]
  addImplicit[BIOAsync3[ZIO]]
  make[BIOTemporal3[ZIO]].from {
    implicit r: zio.clock.Clock =>
      implicitly[BIOTemporal3[ZIO]]
  }
  addImplicit[BIOAsk[ZIO]]
  addImplicit[BIOMonadAsk[ZIO]]
  addImplicit[BIOLocal[ZIO]]
  addImplicit[BIOProfunctor[ZIO]]
  addImplicit[BIOArrow[ZIO]]

  addImplicit[BIOFunctor[IO]]
  addImplicit[BIOBifunctor[IO]]
  addImplicit[BIOApplicative[IO]]
  addImplicit[BIOGuarantee[IO]]
  addImplicit[BIOError[IO]]
  addImplicit[BIOMonad[IO]]
  addImplicit[BIOMonadError[IO]]
  addImplicit[BIOBracket[IO]]
  addImplicit[BIOPanic[IO]]
  addImplicit[BIO[IO]]
  addImplicit[BIOParallel[IO]]
  addImplicit[BIOAsync[IO]]
  make[BIOTemporal[IO]].from {
    implicit r: zio.clock.Clock =>
      implicitly[BIOTemporal[IO]]
  }

  make[zio.clock.Clock.Service].from(zio.clock.Clock.Service.live)
  make[zio.clock.Clock].from(Has(_: zio.clock.Clock.Service))

  make[zio.Runtime[Any]].from((_: ZIORunner).runtime)
  make[BIORunner[IO]].using[ZIORunner]
  make[TracingConfig].fromValue(TracingConfig.enabled)
  make[FailureHandler].from {
    logger: IzLogger =>
      FailureHandler.Custom {
        case BIOExit.Error(error, trace) =>
          logger.warn(s"Fiber errored out due to unhandled $error $trace")
        case BIOExit.Termination(interrupt, (_: InterruptedException) :: _, trace) =>
          logger.trace(s"Fiber interrupted with $interrupt $trace")
        case BIOExit.Termination(defect, _, trace) =>
          logger.warn(s"Fiber terminated erroneously with unhandled $defect $trace")
      }
  }
  make[ZIORunner].from {
    (cpuPool: ThreadPoolExecutor @Id("zio.cpu"), handler: FailureHandler, tracingConfig: TracingConfig) =>
      BIORunner.createZIO(
        cpuPool = cpuPool,
        handler = handler,
        tracingConfig = tracingConfig,
      )
  }
}
