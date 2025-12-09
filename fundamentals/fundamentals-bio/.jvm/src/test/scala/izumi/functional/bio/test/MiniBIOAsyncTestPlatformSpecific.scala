package izumi.functional.bio.test

import izumi.functional.bio.impl.MiniBIOAsync

import java.util.concurrent.Executors
import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext, Promise}

trait MiniBIOAsyncTestPlatformSpecific {
  val parallelEc: ExecutionContext = {
    ExecutionContext.fromExecutor(Executors.newCachedThreadPool())
  }

  def blockingAwait(promise: Promise[Unit]): MiniBIOAsync[Throwable, Unit] = {
    MiniBIOAsync.WeakAsyncForMiniBIOAsync.syncThrowable(Await.result(promise.future, Duration.Inf))
  }
}
