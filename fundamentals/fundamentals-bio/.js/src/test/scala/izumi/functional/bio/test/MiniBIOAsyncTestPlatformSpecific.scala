package izumi.functional.bio.test

import izumi.functional.bio.impl.MiniBIOAsync
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.{ExecutionContext, Promise}

trait MiniBIOAsyncTestPlatformSpecific extends AsyncWordSpec {
  val parallelEc: ExecutionContext = this.executionContext

  def blockingAwait(promise: Promise[Unit]): MiniBIOAsync[Throwable, Unit] = {
    MiniBIOAsync.WeakAsyncForMiniBIOAsync.fromFuture(promise.future)
  }
}
