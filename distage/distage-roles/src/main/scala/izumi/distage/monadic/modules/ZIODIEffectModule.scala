package izumi.distage.monadic.modules

import java.util.concurrent.{Executors, ThreadPoolExecutor}

import distage.Id
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.monadic._
import izumi.distage.roles.services.ResourceRewriter
import izumi.functional.bio.BIORunner.{FailureHandler, ZIORunner}
import izumi.functional.bio._
import logstage.IzLogger
import zio.IO
import zio.internal.tracing.TracingConfig

import scala.concurrent.ExecutionContext

trait ZIODIEffectModule extends ModuleDef {
  make[DIEffectRunner2[IO]].from[DIEffectRunner.BIOImpl[IO]]
  addImplicit[DIEffect2[IO]]
  make[DIEffectAsync2[IO]].from(DIEffectAsync.fromBIOAsync(_: BIOAsync[IO]))

  make[ExecutionContext].named("zio.cpu").from(ExecutionContext.fromExecutor(_: ThreadPoolExecutor @Id("zio.cpu")))
  make[ExecutionContext].named("zio.io").from(ExecutionContext.fromExecutor(_: ThreadPoolExecutor @Id("zio.io")))
  make[ThreadPoolExecutor].named("zio.cpu")
    .fromResource {
      logger: IzLogger =>
        val coresOr2 = Runtime.getRuntime.availableProcessors() max 2
        ResourceRewriter.fromExecutorService(logger, Executors.newFixedThreadPool(coresOr2).asInstanceOf[ThreadPoolExecutor])
    }
  make[ThreadPoolExecutor].named("zio.io")
    .fromResource {
      logger: IzLogger =>
        ResourceRewriter.fromExecutorService(logger, Executors.newCachedThreadPool().asInstanceOf[ThreadPoolExecutor])
    }

  make[BlockingIO[IO]].from {
    blockingPool: ThreadPoolExecutor @Id("zio.io") =>
      BlockingIO.BlockingZIOFromThreadPool(blockingPool)
  }

  addImplicit[BIOTransZio[IO]]
  addImplicit[BIOFork[IO]]
  addImplicit[SyncSafe2[IO]]
  addImplicit[BIOPrimitives[IO]]

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
  make[BIOAsync[IO]].from {
    implicit r: zio.clock.Clock => BIOAsync[IO]
  }

  make[zio.clock.Clock].fromValue(zio.clock.Clock.Live)
  make[zio.Runtime[Any]].from((_: ZIORunner).runtime)
  make[TracingConfig].fromValue(TracingConfig.enabled)
  make[BIORunner[IO]].using[ZIORunner]
  make[ZIORunner].from {
    (cpuPool: ThreadPoolExecutor @Id("zio.cpu"),
     logger: IzLogger,
     tracingConfig: TracingConfig,
    ) =>
      val handler = FailureHandler.Custom {
        case BIOExit.Error(error, trace) =>
          logger.warn(s"Fiber errored out due to unhandled $error $trace")
        case BIOExit.Termination(interrupt, (_: InterruptedException) :: _, trace) =>
          logger.trace(s"Fiber interrupted with $interrupt $trace")
        case BIOExit.Termination(defect, _, trace) =>
          logger.warn(s"Fiber terminated erroneously with unhandled $defect $trace")
      }

      BIORunner.createZIO(
        cpuPool = cpuPool,
        handler = handler,
        tracingConfig = tracingConfig,
      )
  }
}
