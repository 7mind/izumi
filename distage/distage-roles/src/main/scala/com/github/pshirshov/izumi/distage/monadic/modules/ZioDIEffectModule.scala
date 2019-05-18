package com.github.pshirshov.izumi.distage.monadic.modules

import java.util.concurrent.{Executors, ThreadPoolExecutor}

import com.github.pshirshov.izumi.distage.model.definition.ModuleDef
import com.github.pshirshov.izumi.distage.model.monadic.{DIEffect, DIEffectRunner}
import com.github.pshirshov.izumi.distage.roles.services.ResourceRewriter
import com.github.pshirshov.izumi.functional.bio.{BIORunner, BlockingIO}
import distage.Id
import logstage.IzLogger
import scalaz.zio

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
