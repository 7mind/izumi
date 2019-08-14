package izumi.distage.monadic.modules

import java.util.concurrent.{Executors, ThreadPoolExecutor}

import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.monadic.{DIEffect, DIEffectRunner}
import izumi.distage.roles.services.ResourceRewriter
import izumi.functional.bio.{BIOError, BIORunner, BlockingIO}
import distage.Id
import logstage.IzLogger

trait ZioDIEffectModule extends ModuleDef {
  make[DIEffectRunner[zio.IO[Throwable, ?]]].from[DIEffectRunner.BIOImpl[zio.IO]]
  addImplicit[DIEffect[zio.IO[Throwable, ?]]]

  make[ThreadPoolExecutor].named("zio.pool.cpu")
    .fromResource {
      logger: IzLogger =>
        val coresOr2 = Runtime.getRuntime.availableProcessors() max 2
        ResourceRewriter.fromExecutorService(logger, Executors.newFixedThreadPool(coresOr2).asInstanceOf[ThreadPoolExecutor])
    }

  make[ThreadPoolExecutor].named("zio.pool.io")
    .fromResource {
      logger: IzLogger =>
        ResourceRewriter.fromExecutorService(logger, Executors.newCachedThreadPool().asInstanceOf[ThreadPoolExecutor])
    }

  make[BlockingIO[zio.IO]].from {
    blockingPool: ThreadPoolExecutor @Id("zio.pool.io") =>
      BlockingIO.BlockingZIOFromThreadPool(blockingPool)
  }

  addImplicit[BIOError[zio.IO]]

  make[BIORunner[zio.IO]].from {
    (
      cpuPool: ThreadPoolExecutor @Id("zio.pool.cpu"),
      logger: IzLogger,
    ) =>
      val handler = BIORunner.FailureHandler.Custom(message => logger.warn(s"Fiber failed: $message"))

      BIORunner.createZIO(
        cpuPool
        , handler
      )
  }
}
