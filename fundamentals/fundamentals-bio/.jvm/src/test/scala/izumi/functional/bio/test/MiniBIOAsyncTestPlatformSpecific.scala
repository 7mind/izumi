package izumi.functional.bio.test

import izumi.functional.bio.impl.MiniBIOAsync

import java.util.concurrent.{Executors, TimeUnit}
import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext, Promise}

trait MiniBIOAsyncTestPlatformSpecific {
  val parallelEc: ExecutionContext = {
    ExecutionContext.fromExecutor(Executors.newCachedThreadPool())
  }

  def blockingAwait(promise: Promise[Unit]): MiniBIOAsync[Throwable, Unit] = {
    MiniBIOAsync.WeakAsyncForMiniBIOAsync.syncThrowable(Await.result(promise.future, Duration.Inf))
  }

  def withTimeout[A](future: scala.concurrent.Future[A], duration: FiniteDuration)(implicit executionContext: ExecutionContext): scala.concurrent.Future[A] = {
    val scheduler = Executors.newSingleThreadScheduledExecutor()
    val timeoutPromise = Promise[A]()
    val scheduled = scheduler.schedule(
      () => timeoutPromise.failure(new RuntimeException(s"timeout after $duration")),
      duration.toMillis,
      TimeUnit.MILLISECONDS,
    )
    val result = scala.concurrent.Future.firstCompletedOf(Seq(future, timeoutPromise.future))(using executionContext)
    result.andThen { case _ =>
      scheduled.cancel(false)
      scheduler.shutdown()
    }(using executionContext)
  }
}
