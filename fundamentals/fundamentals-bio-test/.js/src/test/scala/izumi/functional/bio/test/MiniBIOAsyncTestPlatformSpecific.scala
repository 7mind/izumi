package izumi.functional.bio.test

import izumi.functional.bio.impl.MiniBIOAsync
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Promise}
import scala.scalajs.js.timers.{SetTimeoutHandle, clearTimeout, setTimeout}

trait MiniBIOAsyncTestPlatformSpecific extends AsyncWordSpec {
  private val asynchronousContext = new ExecutionContext {
    override def execute(command: Runnable): Unit = { val _ = setTimeout(Duration.Zero)(command.run()) }
    override def reportFailure(cause: Throwable): Unit = throw cause
  }

  override implicit def executionContext: ExecutionContext = asynchronousContext

  val parallelEc: ExecutionContext = this.executionContext

  def blockingAwait(promise: Promise[Unit]): MiniBIOAsync[Throwable, Unit] = {
    MiniBIOAsync.WeakAsyncForMiniBIOAsync.fromFuture(promise.future)
  }

  def withTimeout[A](future: scala.concurrent.Future[A], duration: FiniteDuration)(implicit executionContext: ExecutionContext): scala.concurrent.Future[A] = {
    val timeoutPromise = Promise[A]()
    val handle: SetTimeoutHandle = setTimeout(duration) {
      timeoutPromise.failure(new RuntimeException(s"timeout after $duration"))
    }
    val result = scala.concurrent.Future.firstCompletedOf(Seq(future, timeoutPromise.future))(using executionContext)
    result.andThen { case _ => clearTimeout(handle) }(using executionContext)
  }
}
