package izumi.distage.roles.launcher

import cats.effect.{ContextShift, IO, LiftIO}
import izumi.distage.framework.DebugProperties
import izumi.distage.model.effect.QuasiIO
import izumi.functional.bio.{Async2, F}
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.functional.Identity
import izumi.logstage.api.IzLogger
import izumi.logstage.sink.FallbackConsoleSink

import java.util.concurrent.CountDownLatch
import scala.concurrent.{ExecutionContext, Promise}

trait AppShutdownInitiator {
  def releaseAwaitLatch(): Unit
}

object AppShutdownInitiator {
  def empty: AppShutdownInitiator = () => ()
}

/**
  * There are two possible graceful termination paths for an application:
  *
  *   1) User explicitly calls [[AppShutdownStrategy#releaseAwaitLatch]]
  *
  *   2) The application received SIGINT and the shutdown hook triggers.
  *
  *      It's important to remember that all other threads will continue to run until the shutdown hook finishes,
  *      after that they'll stop abruptly without even receiving any exceptions.
  *
  *      Izumi runtime will call [[AppShutdownStrategy#finishShutdown]] when all the cleanups are done.
  *
  *  Possible code paths:
  *
  *    1) [[AppShutdownStrategy#awaitShutdown]] -> [[AppShutdownStrategy#releaseAwaitLatch]] -> [[AppShutdownStrategy#finishShutdown]]
  *    2) [[AppShutdownStrategy#awaitShutdown]] -> [[AppShutdownStrategy#finishShutdown]]
  *
  * @see also [[izumi.distage.roles.launcher.AppShutdownStrategy.ImmediateExitShutdownStrategy]]
  */
trait AppShutdownStrategy[F[_]] extends AppShutdownInitiator {
  def awaitShutdown(logger: IzLogger): F[Unit]
  def releaseAwaitLatch(): Unit

  protected[izumi] def finishShutdown(): Unit
}

object AppShutdownStrategy {
  private[this] val logger = TrivialLogger.make[FallbackConsoleSink](DebugProperties.`izumi.debug.distage.shutdown`.name)

  private[this] def makeShutdownHook(logger: IzLogger, cont: () => Unit): Thread = {
    new Thread(
      () => {
        logger.warn("Termination signal received")
        cont()
      },
      "termination-hook-promise",
    )
  }

  class JvmExitHookLatchShutdownStrategy extends AppShutdownStrategy[Identity] {
    private val primaryLatch = new CountDownLatch(1)
    private val postShutdownLatch = new CountDownLatch(1)

    def awaitShutdown(logger: IzLogger): Unit = {
      val shutdownHook = makeShutdownHook(logger, () => releaseAwaitLatch())
      logger.info("Waiting on latch...")
      Runtime.getRuntime.addShutdownHook(shutdownHook)
      primaryLatch.await()
      try {
        Runtime.getRuntime.removeShutdownHook(shutdownHook)
      } catch {
        case _: IllegalStateException =>
      }
      logger.info("Going to shut down...")
    }

    def releaseAwaitLatch(): Unit = {
      logger.log("Application shutdown requested")
      primaryLatch.countDown()
      postShutdownLatch.await() // we need to let main thread to finish everything
    }

    protected[izumi] override def finishShutdown(): Unit = {
      logger.log("Application will exit now")
      postShutdownLatch.countDown()
    }
  }

  class ImmediateExitShutdownStrategy[F[_]: QuasiIO] extends AppShutdownStrategy[F] {
    def awaitShutdown(logger: IzLogger): F[Unit] = QuasiIO[F].maybeSuspend {
      logger.info("Exiting immediately...")
    }

    override def releaseAwaitLatch(): Unit = {
      logger.log("Application shutdown requested")
    }

    protected[izumi] override def finishShutdown(): Unit = {
      logger.log("Application will exit now")
    }
  }

  class CatsEffectIOShutdownStrategy[F[_]: LiftIO](executionContext: ExecutionContext) extends AppShutdownStrategy[F] {
    private val primaryLatch: Promise[Unit] = Promise[Unit]()
    private val postShutdownLatch: CountDownLatch = new CountDownLatch(1)

    def awaitShutdown(logger: IzLogger): F[Unit] = {
      val shutdownHook = makeShutdownHook(logger, () => releaseAwaitLatch())
      logger.info("Waiting on latch...")
      Runtime.getRuntime.addShutdownHook(shutdownHook)

      val f = primaryLatch.future

      implicit val ec: ExecutionContext = executionContext
      f.onComplete {
        _ =>
          try {
            Runtime.getRuntime.removeShutdownHook(shutdownHook)
          } catch {
            case _: IllegalStateException =>
          }
          logger.info("Going to shut down...")
      }

      implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.Implicits.global)
      val fio = IO.fromFuture(IO.pure(f))
      LiftIO[F].liftIO(fio)
    }

    def releaseAwaitLatch(): Unit = {
      logger.log("Application shutdown requested")
      primaryLatch.success(())
      postShutdownLatch.await() // we need to let main thread to finish everything
    }

    protected[izumi] override def finishShutdown(): Unit = {
      logger.log("Application will exit now")
      postShutdownLatch.countDown()
    }
  }

  class BIOShutdownStrategy[F[+_, +_]: Async2] extends AppShutdownStrategy[F[Throwable, _]] {
    private val primaryLatch: Promise[Unit] = Promise[Unit]()
    private val postShutdownLatch: CountDownLatch = new CountDownLatch(1)

    def awaitShutdown(logger: IzLogger): F[Throwable, Unit] = {
      val shutdownHook = makeShutdownHook(logger, () => releaseAwaitLatch())
      logger.info("Waiting on latch...")
      Runtime.getRuntime.addShutdownHook(shutdownHook)

      F.fromFuture {
        implicit ec =>
          primaryLatch.future.map[Unit] {
            _ =>
              try {
                Runtime.getRuntime.removeShutdownHook(shutdownHook)
              } catch {
                case _: IllegalStateException =>
              }
              logger.info("Going to shut down...")
          }
      }
    }

    def releaseAwaitLatch(): Unit = {
      logger.log("Application shutdown requested")
      primaryLatch.success(())
      postShutdownLatch.await() // we need to let main thread to finish everything
    }

    protected[izumi] override def finishShutdown(): Unit = {
      logger.log("Application will exit now")
      postShutdownLatch.countDown()
    }
  }

}
