package izumi.distage.roles.launcher

import izumi.distage.framework.DebugProperties
import izumi.functional.bio.{Async2, IO2}
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.logstage.api.IzLogger

import java.util.concurrent.CountDownLatch
import scala.concurrent.Promise

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
trait AppShutdownStrategy[F[+_, +_]] extends AppShutdownInitiator {
  def awaitShutdown(logger: IzLogger)(implicit F: IO2[F], FA: Async2[F]): F[Throwable, Unit]
  def releaseAwaitLatch(): Unit
  def finishShutdown(): Unit
}

object AppShutdownStrategy {
  private val debugLogger: TrivialLogger = TrivialLogger.make[AppShutdownStrategy.type](DebugProperties.`izumi.debug.distage.shutdown`.name)

  private def makeShutdownHook(logger: IzLogger, cont: () => Unit): Thread = {
    new Thread(
      () => {
        logger.warn("Termination signal received")
        cont()
      },
      "termination-hook-promise",
    )
  }

  class JvmExitHookBlockingShutdownStrategy[F[+_, +_]] extends AppShutdownStrategy[F] {
    private val primaryLatch = new CountDownLatch(1)
    private val postShutdownLatch = new CountDownLatch(1)

    override def awaitShutdown(logger: IzLogger)(implicit F: IO2[F], FA: Async2[F]): F[Throwable, Unit] = {
      F.syncThrowable {
        scala.concurrent.blocking {
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
      }
    }

    override def releaseAwaitLatch(): Unit = {
      debugLogger.log("Application shutdown requested")
      primaryLatch.countDown()
      postShutdownLatch.await() // we need to let the main thread finish everything
    }

    override def finishShutdown(): Unit = {
      debugLogger.log("Application will exit now")
      postShutdownLatch.countDown()
    }
  }

  class ImmediateExitShutdownStrategy[F[+_, +_]] extends AppShutdownStrategy[F] {
    def awaitShutdown(logger: IzLogger)(implicit F: IO2[F], FA: Async2[F]): F[Throwable, Unit] = F.syncThrowable {
      logger.info("Exiting immediately...")
    }

    override def releaseAwaitLatch(): Unit = {
      debugLogger.log("Application shutdown requested")
    }

    override def finishShutdown(): Unit = {
      debugLogger.log("Application will exit now")
    }
  }

  class AsyncShutdownStrategy[F[+_, +_]] extends AppShutdownStrategy[F] {
    private val primaryLatch: Promise[Unit] = Promise[Unit]()
    private val postShutdownLatch: CountDownLatch = new CountDownLatch(1)

    override def awaitShutdown(logger: IzLogger)(implicit F: IO2[F], FA: Async2[F]): F[Throwable, Unit] = {
      for {
        shutdownHook <- F.syncThrowable {
          val shutdownHook = makeShutdownHook(logger, () => releaseAwaitLatch())
          logger.info("Waiting on latch...")
          Runtime.getRuntime.addShutdownHook(shutdownHook)
          shutdownHook
        }
        _ <- FA.fromFuture(primaryLatch.future)
        _ <- F.syncThrowable {
          try {
            Runtime.getRuntime.removeShutdownHook(shutdownHook)
          } catch {
            case _: Throwable =>
          }
          logger.info("Going to shut down...")
        }
      } yield ()
    }

    override def releaseAwaitLatch(): Unit = {
      debugLogger.log("Application shutdown requested")
      primaryLatch.success(())
      postShutdownLatch.await() // we need to let main thread to finish everything
    }

    override def finishShutdown(): Unit = {
      debugLogger.log("Application will exit now")
      postShutdownLatch.countDown()
    }
  }

//  class BIOShutdownStrategy[F[+_, +_]: Async2] extends AppShutdownStrategy[F[Throwable, _]] {
//    private val primaryLatch: Promise[Unit] = Promise[Unit]()
//    private val postShutdownLatch: CountDownLatch = new CountDownLatch(1)
//
//    override def awaitShutdown(logger: IzLogger): F[Throwable, Unit] = {
//      val shutdownHook = makeShutdownHook(logger, () => releaseAwaitLatch())
//      logger.info("Waiting on latch...")
//      Runtime.getRuntime.addShutdownHook(shutdownHook)
//
//      F.fromFuture(_ => primaryLatch.future) *> F.sync {
//        try {
//          Runtime.getRuntime.removeShutdownHook(shutdownHook)
//        } catch {
//          case _: Throwable =>
//        }
//        logger.info("Going to shut down...")
//      }
//    }
//
//    override def releaseAwaitLatch(): Unit = {
//      debugLogger.log("Application shutdown requested")
//      primaryLatch.success(())
//      postShutdownLatch.await() // we need to let main thread to finish everything
//    }
//
//    override def finishShutdown(): Unit = {
//      debugLogger.log("Application will exit now")
//      postShutdownLatch.countDown()
//    }
//  }

}
