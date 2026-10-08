package izumi.distage.testkit.scalatest

import izumi.distage.testkit.model.DistageTest
import izumi.distage.testkit.scalatest.LegacyRuntimeFinalizationTest.{EmptyReporter, Outer, RecordingControl}
import izumi.distage.testkit.scalatest.LegacyRuntimeReleaseTest.TrackedContext
import izumi.distage.testkit.runner.TestkitRunnerModule
import izumi.distage.testkit.runner.di.TestRuntime
import izumi.fundamentals.platform.IzPlatform
import izumi.functional.bio.impl.MiniBIOAsync
import izumi.functional.lifecycle.Lifecycle
import izumi.functional.quasi.QuasiIORunner
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks.Discarder
import izumi.distage.testkit.runner.spec.AnyWordSpec

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.{Success, Try}

final class LegacyRuntimeCallerTest extends AnyWordSpec {
  "The legacy JVM caller" should {
    "wait for finalization after interruption and restore its interrupt flag" in {
      assert(LegacyRuntimeCallerTest.check(failures = false))
    }
    "retain interruption alongside execution and release failures" in {
      assert(LegacyRuntimeCallerTest.check(failures = true))
    }
  }
}

object LegacyRuntimeCallerTest {
  private final val Deadline = 10.seconds
  private final val Observation = 250.millis

  private def check(failures: Boolean): Boolean = {
    val context = new TrackedContext
    implicit val ec: ExecutionContext = context
    val engineCompleted = Promise[Unit]()
    val callbackRegistered = Promise[Unit]()
    val callbackObserved = Promise[Unit]()
    val executionGate = Promise[Unit]()
    val releaseCompleted = Promise[Unit]()
    val returned = Promise[Try[Unit]]()
    val released = new AtomicInteger(0)
    val executionFailure = new IllegalStateException("Controlled caller execution failure")
    val releaseFailure = new IllegalStateException("Controlled caller release failure")
    val interruptedAfterReturn = Promise[Boolean]()
    val base = QuasiIORunner.fromBIO[MiniBIOAsync](using MiniBIOAsync.UnsafeRunMiniBIOAsync(using context))
    val runner = new QuasiIORunner[Outer] {
      override def runBlocking[A](effect: => Outer[A]): A = base.runBlocking(effect)
      override def runFuture[A](effect: => Outer[A]): Future[A] = base.runFuture(effect)
      override def runFutureInterruptible[A](effect: => Outer[A]): (Future[A], () => Future[Unit]) = {
        val (future, stop) = base.runFutureInterruptible(effect)
        val gated = future.transformWith {
          result =>
            engineCompleted.success(())
            executionGate.future.transform(_ => if (failures) scala.util.Failure(executionFailure) else result)
        }
        (gated, stop)
      }
    }
    val lifecycle = Lifecycle.makeSimple[QuasiIORunner[Outer]](runner) {
      _ =>
        try {
          context.close()
          released.incrementAndGet()
          if (failures) throw releaseFailure
        } finally { val _ = releaseCompleted.trySuccess(()) }
    }
    val control = new RecordingControl
    val actualRuntime = new TestRuntime[Outer](lifecycle, IzPlatform.platformGlobalExecutionContext)
    def dispatch(): Unit = {
      val operation = actualRuntime.run(TestkitRunnerModule.run[Outer](new EmptyReporter, _ => false, Seq.empty[DistageTest[Identity]], Nil))
      operation.completion.onComplete(_ => callbackObserved.trySuccess(()).discard())(IzPlatform.platformGlobalExecutionContext)
      callbackRegistered.success(()).discard()
      val result = try { val _ = operation.awaitCompletion(); None } catch { case cause: Throwable => Some(cause) }
      control.completeOuterSuite(result)
    }
    val caller = new Thread(
      () => {
        val attempted: Try[Unit] =
          try Success(dispatch())
          catch { case cause: Throwable => scala.util.Failure(cause) }
        interruptedAfterReturn.success(Thread.currentThread().isInterrupted).discard()
        returned.success(attempted).discard()
      },
      "legacy-caller-interruption-probe",
    )
    caller.start()
    val observed = Try {
      Await.result(engineCompleted.future, Deadline)
      Await.result(callbackRegistered.future, Deadline)
      require(caller.isAlive, "The legacy JVM caller must be awaiting its result")
      caller.interrupt()
      Thread.sleep(Observation.toMillis)
      val premature = control.completed.isCompleted
      println("LEGACY_CALLER_INTERRUPTION premature=" + premature + " released=" + released.get() + " callerReturned=" + returned.isCompleted)
      !premature && released.get() == 0
    }
    val cleaned = Try {
      val _ = executionGate.trySuccess(())
      try {
        Await.result(releaseCompleted.future, Deadline)
        val _ = Await.result(control.completed.future, Deadline)
        val result = Await.result(returned.future, Deadline)
        Await.result(callbackObserved.future, Deadline)
        caller.join(Deadline.toMillis)
        require(!caller.isAlive && released.get() == 1 && result == Success(()), "The legacy caller and its allocation must drain")
      } finally {
        context.close()
        caller.join(Deadline.toMillis)
      }
      println("LEGACY_CALLER_INTERRUPTION_CLEANUP released=1 callerTerminated=true executorTerminated=true")
    }
    require(cleaned.isSuccess, "Legacy caller probe cleanup failed: " + cleaned)
    val outcome = Await.result(control.completed.future, Deadline)
    def includes(cause: Throwable, expected: Throwable, ancestors: List[Throwable]): Boolean = {
      if (ancestors.exists(_ eq cause)) false
      else (cause eq expected) || (Option(cause.getCause).toList ++ cause.getSuppressed.toList).exists(next => includes(next, expected, cause :: ancestors))
    }
    val retained =
      if (failures) outcome.exists(cause => includes(cause, executionFailure, Nil) && includes(cause, releaseFailure, Nil))
      else outcome.exists(_.isInstanceOf[InterruptedException])
    val flag = Await.result(interruptedAfterReturn.future, Deadline)
    println("LEGACY_CALLER_OUTCOME failures=" + failures + " retained=" + retained + " interruptFlag=" + flag)
    def hasInterruption(cause: Throwable, ancestors: List[Throwable]): Boolean = {
      if (ancestors.exists(_ eq cause)) false
      else
        cause.isInstanceOf[InterruptedException] || (Option(cause.getCause).toList ++ cause.getSuppressed.toList).exists(
          next => hasInterruption(next, cause :: ancestors)
        )
    }
    observed.get && retained && flag && outcome.exists(cause => hasInterruption(cause, Nil))
  }

}
