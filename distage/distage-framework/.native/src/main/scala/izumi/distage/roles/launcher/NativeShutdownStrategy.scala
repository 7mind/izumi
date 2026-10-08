package izumi.distage.roles.launcher

import izumi.functional.quasi.{QuasiAsync, QuasiIO}
import izumi.fundamentals.platform.language.Quirks.Discarder
import izumi.logstage.api.IzLogger

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Promise

object NativeShutdownStrategy {
  private[roles] trait LauncherShutdownCompletion {
    private[roles] def deferShutdownCompletion(): Unit
    private[roles] def completeShutdown(): Unit
  }
}

final class NativeShutdownStrategy[F[_]] extends AppShutdownStrategy[F] with NativeShutdownStrategy.LauncherShutdownCompletion {
  private val primaryLatch = Promise[Unit]()
  private val postShutdownLatch = new CountDownLatch(1)
  private val shutdownHook = new AtomicReference(Option.empty[Thread])
  private val completionOwner = new AtomicReference(Option.empty[Thread])

  override private[roles] def deferShutdownCompletion(): Unit = {
    require(completionOwner.compareAndSet(None, Some(Thread.currentThread())), "Launcher shutdown completion was deferred more than once")
  }

  override def awaitShutdown(logger: IzLogger)(implicit F: QuasiIO[F], FA: QuasiAsync[F]): F[Unit] = {
    import QuasiIO.syntax.*

    for {
      _ <- F.maybeSuspend {
        val hook = new Thread(
          () => {
            logger.warn("Termination signal received")
            releaseAwaitLatch()
          },
          "termination-hook-promise",
        )
        if (!shutdownHook.compareAndSet(None, Some(hook))) {
          throw new IllegalStateException("Application shutdown strategy is already awaiting shutdown")
        }
        Runtime.getRuntime.addShutdownHook(hook)
        logger.info("Waiting on latch...")
      }
      _ <- FA.fromFuture(primaryLatch.future)
      _ <- F.maybeSuspend(logger.info("Going to shut down..."))
    } yield ()
  }

  override def releaseAwaitLatch(): Unit = {
    primaryLatch.trySuccess(()).discard()
    // The launcher thread must return from its request to release the scope that completes shutdown.
    if (!completionOwner.get().exists(_ eq Thread.currentThread())) postShutdownLatch.await()
  }

  override def finishShutdown(): Unit = {
    if (completionOwner.get().isEmpty) completeShutdown()
  }

  override private[roles] def completeShutdown(): Unit = {
    // Native holds its hook registry monitor while joining hooks; release the hook before unregistering it.
    postShutdownLatch.countDown()
    shutdownHook.get().foreach {
      hook =>
        try {
          Runtime.getRuntime.removeShutdownHook(hook).discard()
        } catch {
          case _: IllegalStateException =>
        }
    }
  }
}
