package izumi.distage.roles.launcher

import java.util.concurrent.CountDownLatch

import cats.effect.{ContextShift, IO, LiftIO}
import izumi.distage.model.effect.QuasiIO
import izumi.functional.bio.{BIOAsync, F}
import izumi.fundamentals.platform.functional.Identity
import izumi.logstage.api.IzLogger

import scala.concurrent.{ExecutionContext, Promise}

trait AppShutdownStrategy[F[_]] {
  def await(logger: IzLogger): F[Unit]
  def release(): Unit
}

object AppShutdownStrategy {

  class JvmExitHookLatchShutdownStrategy extends AppShutdownStrategy[Identity] {
    private val latch = new CountDownLatch(1)
    private val mainLatch = new CountDownLatch(1)

    override def release(): Unit = mainLatch.countDown()

    def stop(): Unit = {
      latch.countDown()
      mainLatch.await() // we need to let main thread to finish everything
    }

    def await(logger: IzLogger): Unit = {
      val shutdownHook = new Thread(
        () => {
          stop()
        },
        "termination-hook-latch",
      )

      logger.info("Waiting on latch...")
      Runtime.getRuntime.addShutdownHook(shutdownHook)
      latch.await()
      try {
        Runtime.getRuntime.removeShutdownHook(shutdownHook)
      } catch {
        case _: IllegalStateException =>
      }
      logger.info("Going to shut down...")
    }
  }

  class ImmediateExitShutdownStrategy[F[_]: QuasiIO] extends AppShutdownStrategy[F] {
    def await(logger: IzLogger): F[Unit] = {
      QuasiIO[F].maybeSuspend {
        logger.info("Exiting immediately...")
      }
    }

    override def release(): Unit = {}
  }

  class CatsEffectIOShutdownStrategy[F[_]: LiftIO](executionContext: ExecutionContext) extends AppShutdownStrategy[F] {
    private val shutdownPromise: Promise[Unit] = Promise[Unit]()
    private val mainLatch: CountDownLatch = new CountDownLatch(1)

    override def release(): Unit = {
      mainLatch.countDown()
    }

    def stop(): Unit = {
      shutdownPromise.success(())
      mainLatch.await() // we need to let main thread to finish everything
    }

    def await(logger: IzLogger): F[Unit] = {
      val shutdownHook = new Thread(
        () => {
          stop()
        },
        "termination-hook-promise",
      )

      logger.info("Waiting on latch...")
      Runtime.getRuntime.addShutdownHook(shutdownHook)

      val f = shutdownPromise.future

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
  }

  class BIOShutdownStrategy[F[+_, +_]: BIOAsync] extends AppShutdownStrategy[F[Throwable, ?]] {
    private val shutdownPromise: Promise[Unit] = Promise[Unit]()
    private val mainLatch: CountDownLatch = new CountDownLatch(1)

    override def release(): Unit = {
      mainLatch.countDown()
    }

    def stop(): Unit = {
      shutdownPromise.success(())
      mainLatch.await() // we need to let main thread to finish everything
    }

    def await(logger: IzLogger): F[Throwable, Unit] = {
      val shutdownHook = new Thread(
        () => {
          stop()
        },
        "termination-hook-promise",
      )

      logger.info("Waiting on latch...")
      Runtime.getRuntime.addShutdownHook(shutdownHook)

      val f = shutdownPromise.future

      F.fromFuture {
        implicit ec =>
          f.map[Unit] {
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
  }

}
