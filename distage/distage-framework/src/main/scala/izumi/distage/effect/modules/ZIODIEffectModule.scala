package izumi.distage.effect.modules

import java.util.concurrent.{Executors, ThreadPoolExecutor}

import distage.Id
import izumi.distage.model.definition.{DIResource, ModuleDef}
import izumi.distage.model.effect._
import izumi.functional.bio.BIORunner.{FailureHandler, ZIORunner}
import izumi.functional.bio._
import izumi.logstage.api.IzLogger
import zio.internal.tracing.TracingConfig
import zio.{IO, ZIO}

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

  make[BlockingIO3[ZIO]].from {
    blockingPool: ThreadPoolExecutor @Id("zio.io") =>
      BlockingIOInstances.BlockingZIOFromThreadPool(blockingPool)
  }
  make[BlockingIO[IO]].from {
    implicit b: BlockingIO3[ZIO] =>
      implicitly[BlockingIO[IO]]
  }

  addImplicit[BIOTransZio[IO]]
  addImplicit[BIOFork3[ZIO]]
  addImplicit[BIOFork[IO]]
  addImplicit[SyncSafe3[ZIO]]
  addImplicit[SyncSafe2[IO]]
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
  addImplicit[BIOAsync[IO]]
  make[BIOTemporal[IO]].from {
    implicit r: zio.clock.Clock =>
      implicitly[BIOTemporal[IO]]
  }

  make[zio.clock.Clock].from(zio.Has(zio.clock.Clock.Service.live))

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
