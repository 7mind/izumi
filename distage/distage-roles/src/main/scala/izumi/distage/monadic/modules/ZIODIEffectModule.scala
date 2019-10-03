package izumi.distage.monadic.modules

import java.util.concurrent.{Executors, ThreadPoolExecutor}

import distage.Id
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.monadic.{DIEffect, DIEffectRunner}
import izumi.distage.roles.services.ResourceRewriter
import izumi.functional.bio.BIORunner.ZIORunner
import izumi.functional.bio._
import logstage.IzLogger
import zio.IO
import zio.internal.tracing.TracingConfig

trait ZIODIEffectModule extends ModuleDef {
  make[DIEffectRunner[IO[Throwable, ?]]].from[DIEffectRunner.BIOImpl[IO]]
  addImplicit[DIEffect[IO[Throwable, ?]]]

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

  addImplicit[BIO[IO]]
  addImplicit[BIOTransZio[IO]]
  addImplicit[BIOFork[IO]]
  addImplicit[BIOFunctor[IO]]
  addImplicit[BIOBifunctor[IO]]
  addImplicit[BIOApplicative[IO]]
  addImplicit[BIOGuarantee[IO]]
  addImplicit[BIOMonad[IO]]
  addImplicit[BIOError[IO]]
  addImplicit[BIOMonadError[IO]]
  addImplicit[BIOBracket[IO]]
  addImplicit[BIOPanic[IO]]
  addImplicit[SyncSafe2[IO]]
  make[BIOAsync[IO]].from {
    implicit r: zio.clock.Clock => BIOAsync[IO]
  }

  make[zio.clock.Clock].fromValue(zio.clock.Clock.Live)
  make[zio.Runtime[Any]].from((_: ZIORunner).runtime)
  make[TracingConfig].from(TracingConfig.enabled)
  make[BIORunner[IO]].using[ZIORunner]
  make[ZIORunner].from {
    (cpuPool: ThreadPoolExecutor @Id("zio.cpu"),
     logger: IzLogger,
     tracingConfig: TracingConfig) =>
      val handler = BIORunner.FailureHandler.Custom(message => logger.warn(s"Fiber failed: $message"))
      BIORunner.createZIO(
        cpuPool = cpuPool,
        handler = handler,
        tracingConfig = tracingConfig,
      )
  }
}
