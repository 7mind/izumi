package izumi.distage.testkit.scalatest

import izumi.distage.testkit.scalatest.LegacyRuntimeFinalizationTest.Outer
import izumi.functional.quasi.QuasiIORunner
import org.scalatest.wordspec.AsyncWordSpec

import java.util.concurrent.{Executors, TimeUnit}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Promise}

trait LegacyRuntimeFinalizationTestPlatformSpecific extends AsyncWordSpec {
  def pause(duration: FiniteDuration): Future[Unit] = {
    val scheduler = Executors.newSingleThreadScheduledExecutor()
    val promise = Promise[Unit]()
    val action: Runnable = () => { val _ = promise.trySuccess(()); scheduler.shutdown() }
    val _ = scheduler.schedule(action, duration.toMillis, TimeUnit.MILLISECONDS)
    promise.future
  }

  def withTimeout[A](future: Future[A], duration: FiniteDuration): Future[A] = {
    val scheduler = Executors.newSingleThreadScheduledExecutor()
    val timeout = Promise[A]()
    val scheduled =
      scheduler.schedule(() => timeout.tryFailure(new AssertionError(s"Runtime completion timed out after $duration")), duration.toMillis, TimeUnit.MILLISECONDS)
    Future.firstCompletedOf(Seq(future, timeout.future)).andThen {
      case _ =>
        val _ = scheduled.cancel(false)
        scheduler.shutdown()
    }
  }
}

abstract class LegacyRuntimeFinalizationRunnerPlatformSpecific(base: QuasiIORunner[Outer]) extends QuasiIORunner[Outer] {
  override def runBlocking[A](effect: => Outer[A]): A = base.runBlocking(effect)
  override def runFuture[A](effect: => Outer[A]): Future[A] = base.runFuture(effect)
}
