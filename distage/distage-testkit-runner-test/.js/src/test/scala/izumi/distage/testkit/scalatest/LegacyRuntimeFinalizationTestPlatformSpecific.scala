package izumi.distage.testkit.scalatest

import izumi.distage.testkit.scalatest.LegacyRuntimeFinalizationTest.Outer
import izumi.functional.quasi.QuasiIORunner
import izumi.distage.testkit.runner.spec.AsyncWordSpec

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js.timers.{clearTimeout, setTimeout}

trait LegacyRuntimeFinalizationTestPlatformSpecific extends AsyncWordSpec {
  private val asynchronousContext = new ExecutionContext {
    override def execute(command: Runnable): Unit = { val _ = setTimeout(Duration.Zero)(command.run()) }
    override def reportFailure(cause: Throwable): Unit = throw cause
  }

  override implicit def executionContext: ExecutionContext = asynchronousContext

  def pause(duration: FiniteDuration): Future[Unit] = {
    val promise = Promise[Unit]()
    val _ = setTimeout(duration) { val _ = promise.trySuccess(()) }
    promise.future
  }

  def withTimeout[A](future: Future[A], duration: FiniteDuration): Future[A] = {
    val timeout = Promise[A]()
    val handle = setTimeout(duration) { val _ = timeout.tryFailure(new AssertionError(s"Runtime completion timed out after $duration")) }
    Future.firstCompletedOf(Seq(future, timeout.future)).andThen { case _ => clearTimeout(handle) }
  }
}

abstract class LegacyRuntimeFinalizationRunnerPlatformSpecific(base: QuasiIORunner[Outer]) extends QuasiIORunner[Outer] {
  override def runFuture[A](effect: => Outer[A]): Future[A] = base.runFuture(effect)
}
