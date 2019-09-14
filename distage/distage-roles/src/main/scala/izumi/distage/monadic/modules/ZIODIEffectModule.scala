package izumi.distage.monadic.modules

import java.util.concurrent.{Executors, ThreadPoolExecutor}

import distage.Id
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.monadic.{DIEffect, DIEffectRunner}
import izumi.distage.roles.services.ResourceRewriter
import izumi.functional.bio._
import logstage.IzLogger
import zio.IO

trait ZIODIEffectModule extends ModuleDef {
  make[DIEffectRunner[IO[Throwable, ?]]].from[DIEffectRunner.BIOImpl[IO]]
  addImplicit[DIEffect[IO[Throwable, ?]]]

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

  make[BlockingIO[IO]].from {
    blockingPool: ThreadPoolExecutor @Id("zio.pool.io") =>
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

  make[BIORunner[IO]].from {
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
