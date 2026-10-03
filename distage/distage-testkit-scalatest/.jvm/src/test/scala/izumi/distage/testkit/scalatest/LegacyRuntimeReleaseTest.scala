package izumi.distage.testkit.scalatest

import izumi.distage.testkit.model.{DistageTest, EnvResult}
import izumi.distage.testkit.services.scalatest.dstest.TestRunnerRuntime
import izumi.distage.testkit.services.scalatest.dstest.TestRunnerRuntime.AsyncResult
import izumi.functional.bio.impl.MiniBIOAsync
import izumi.functional.lifecycle.Lifecycle
import izumi.functional.quasi.QuasiIORunner
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks.Discarder

import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{Await, ExecutionContext, Promise}
import scala.concurrent.duration.*
import scala.util.{Failure, Success, Try}

final class LegacyRuntimeReleaseTest extends AnyWordSpec {
  "The legacy runtime" should {
    "keep its callback pending while allocation release is held" in {
      assert(LegacyRuntimeReleaseTest.check(failRelease = false))
    }
    "report an allocation release failure after closing its executor" in {
      assert(LegacyRuntimeReleaseTest.check(failRelease = true))
    }
  }
}

object LegacyRuntimeReleaseTest {
  import LegacyRuntimeFinalizationTest.{EmptyReporter, RecordingControl}

  private final val Deadline = 10.seconds
  private final val Observation = 250.millis
  private final val Threads = 4
  private final val DrainPoll = 10.millis
  private type Outer[A] = MiniBIOAsync[Throwable, A]

  private[scalatest] final class TrackedContext extends ExecutionContext {
    private val executor = Executors.newFixedThreadPool(Threads)
    private val lock = new Object
    private var pending = 0
    private var errors = Vector.empty[Throwable]
    override def execute(task: Runnable): Unit = {
      lock.synchronized(pending += 1)
      try
        executor.execute(
          () =>
            try task.run()
            finally lock.synchronized { pending -= 1; lock.notifyAll() }
        )
      catch {
        case cause: Throwable => lock.synchronized { pending -= 1; lock.notifyAll() }; throw cause
      }
    }
    override def reportFailure(cause: Throwable): Unit = lock.synchronized(errors :+= cause)
    def close(): Unit = {
      val limit = System.nanoTime() + Deadline.toNanos
      val drained =
        try
          lock.synchronized {
            while (pending != 0 && System.nanoTime() < limit) lock.wait(DrainPoll.toMillis)
            pending == 0
          }
        finally executor.shutdown()
      val terminated = executor.awaitTermination(Deadline.toMillis, TimeUnit.MILLISECONDS)
      if (!terminated) {
        executor.shutdownNow().discard()
        executor.awaitTermination(Deadline.toMillis, TimeUnit.MILLISECONDS).discard()
      }
      require(drained && terminated, "Legacy probe callbacks must drain and its executor must terminate")
      require(lock.synchronized(errors.isEmpty), "Legacy probe execution context must report no late failures")
    }
  }

  private def check(failRelease: Boolean): Boolean = {
    val context = new TrackedContext
    val acquired = new AtomicInteger(0)
    val released = new AtomicInteger(0)
    val callbacks = new AtomicInteger(0)
    val releaseEntered = Promise[Unit]()
    val releaseCompleted = Promise[Unit]()
    val releaseGate = new CountDownLatch(if (failRelease) 0 else 1)
    val original = new IllegalStateException("Controlled legacy allocation release failure")
    val lifecycle = Lifecycle.makeSimple[QuasiIORunner[Outer]] {
      acquired.incrementAndGet()
      QuasiIORunner.fromBIO[MiniBIOAsync](using MiniBIOAsync.UnsafeRunMiniBIOAsync(using context))
    } {
      _ =>
        try {
          releaseEntered.success(())
          require(releaseGate.await(Deadline.toMillis, TimeUnit.MILLISECONDS), "Held legacy allocation release timed out")
          context.close()
          released.incrementAndGet()
          if (failRelease) throw original
        } finally { val _ = releaseCompleted.trySuccess(()) }
    }
    val runtime = TestRunnerRuntime.asyncRuntimeFor[Outer](lifecycle, Nil)
    val callback = Promise[Either[Throwable, List[EnvResult]]]()
    val started: AsyncResult[List[EnvResult]] = runtime.runTests(new RecordingControl, new EmptyReporter, _ => false, Seq.empty[DistageTest[Identity]]) match {
      case Right(value) => value
      case Left(value) => throw new AssertionError("Expected an asynchronous legacy result, got " + value)
    }
    started.resultCallback {
      result =>
        callbacks.incrementAndGet(); val _ = callback.trySuccess(result)
    }
    val observed = Try {
      Await.result(releaseEntered.future, Deadline)
      if (failRelease) {
        Await.result(releaseCompleted.future, Deadline)
        val result = Await.result(callback.future, Deadline)
        val retained = result.left.toOption.exists(_ eq original)
        println("LEGACY_RELEASE_FAILURE_CALLBACK retained=" + retained + " callback=" + result + " released=" + released.get())
        retained
      } else {
        Thread.sleep(Observation.toMillis)
        val held = !callback.isCompleted && acquired.get() == 1 && released.get() == 0
        println(
          "LEGACY_RELEASE_HELD_CALLBACK completed=" + callback.isCompleted + " acquired=" + acquired.get() + " released=" + released.get() + " callbacks=" + callbacks
            .get()
        )
        held
      }
    }
    val cleaned = Try {
      releaseGate.countDown()
      try {
        Await.result(releaseCompleted.future, Deadline)
        val _ = Await.result(callback.future, Deadline)
        started.earlyShutdown()
        started.earlyShutdown()
        require(acquired.get() == 1 && released.get() == 1 && callbacks.get() == 1, "Legacy allocation and callback must settle exactly once")
      } finally context.close()
      println("LEGACY_RELEASE_CALLBACK_CLEANUP failRelease=" + failRelease + " acquired=1 released=1 callbacks=1 executorTerminated=true")
    }
    (observed, cleaned) match {
      case (Success(passed), Success(_)) => passed
      case (Failure(primary), Failure(cleanup)) => primary.addSuppressed(cleanup); throw primary
      case (Failure(primary), _) => throw primary
      case (_, Failure(cleanup)) => throw cleanup
    }
  }

}
