package izumi.distage.testkit.spec

import izumi.distage.testkit.runner.impl.RunnerToF
import izumi.functional.bio.impl.MiniBIOAsync
import izumi.functional.lifecycle.Lifecycle
import izumi.functional.quasi.{QuasiAsync, QuasiIO, QuasiIORunner}
import izumi.fundamentals.platform.functional.Identity

import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.concurrent.duration.*

private[spec] object RunnerCompletionFixtures {
  private final val Deadline = 10.seconds
  private final val Observation = 250.millis
  private type Outer[A] = MiniBIOAsync[Throwable, A]

  private sealed trait InterruptResult {
    def name: String
    def finish(signal: Future[Unit], cause: Throwable): Future[Unit]
  }
  private case object SuccessfulInterrupt extends InterruptResult {
    override val name = "successful-interrupt"
    override def finish(signal: Future[Unit], cause: Throwable): Future[Unit] = { val _ = cause; signal }
  }
  private case object FailedInterrupt extends InterruptResult {
    override val name = "failed-interrupt-future"
    override def finish(signal: Future[Unit], cause: Throwable): Future[Unit] = { val _ = signal; Future.failed(cause) }
  }
  private case object ThrowingInterrupt extends InterruptResult {
    override val name = "throwing-interrupt-action"
    override def finish(signal: Future[Unit], cause: Throwable): Future[Unit] = { val _ = signal; throw cause }
  }

  private final class TrackedContext extends ExecutionContext {
    private val executor = Executors.newSingleThreadExecutor()
    private val lock = new Object
    private var pending = 0
    private var errors = Vector.empty[Throwable]
    override def execute(task: Runnable): Unit = {
      lock.synchronized { pending += 1 }
      executor.execute(() => try task.run() finally lock.synchronized { pending -= 1; lock.notifyAll() })
    }
    override def reportFailure(cause: Throwable): Unit = lock.synchronized { errors :+= cause }
    def close(): Unit = {
      val limit = System.nanoTime() + Deadline.toNanos
      lock.synchronized {
        while (pending != 0 && System.nanoTime() < limit) lock.wait(10)
        require(pending == 0, "Bridge callbacks must drain")
      }
      executor.shutdown()
      require(executor.awaitTermination(Deadline.toMillis, TimeUnit.MILLISECONDS), "Bridge executor must terminate")
      require(lock.synchronized(errors.isEmpty), "Bridge callbacks must report no late failures")
    }
  }

  def checks(): Vector[(String, Boolean)] = Vector[InterruptResult](SuccessfulInterrupt, FailedInterrupt, ThrowingInterrupt).flatMap(check)

  private def check(interruptResult: InterruptResult): Vector[(String, Boolean)] = {
    val context = new TrackedContext
    implicit val ec: ExecutionContext = context
    val bodyEntered = Promise[Unit]()
    val releaseEntered = Promise[Unit]()
    val incomingCompleted = Promise[Unit]()
    val bodyGate = new CountDownLatch(1)
    val releaseGate = new CountDownLatch(1)
    val acquired = new AtomicInteger(0)
    val released = new AtomicInteger(0)
    val requests = new AtomicInteger(0)
    val interruptionFailure = new IllegalStateException("Controlled incoming interruption failure")
    val incoming = new QuasiIORunner[Identity] {
      private val delegate = QuasiIORunner.IdentityImpl
      def runBlocking[A](effect: => A): A = effect
      override def runFuture[A](effect: => A): Future[A] = delegate.runFuture(effect)
      override def runFutureInterruptible[A](effect: => A): (Future[A], () => Future[Unit]) = {
        val (future, stop) = delegate.runFutureInterruptible(effect)
        val requested = () => {
          requests.incrementAndGet()
          interruptResult.finish(stop.apply(), interruptionFailure)
        }
        (future.andThen { case _ => incomingCompleted.success(()); () }, requested)
      }
    }
    val bridge = new RunnerToF.AsyncImpl[Outer](QuasiIO[Outer], QuasiAsync[Outer])
    val resource = Lifecycle.makeSimple { acquired.incrementAndGet(); () } { _ =>
      releaseEntered.success(())
      require(releaseGate.await(Deadline.toMillis, TimeUnit.MILLISECONDS), "Held Identity release timed out")
      released.incrementAndGet()
      ()
    }
    val effect = bridge.runToF[Identity, Unit](incoming, () => resource.use { _ =>
      bodyEntered.success(())
      bodyGate.await()
      ()
    })
    val runner = MiniBIOAsync.UnsafeRunMiniBIOAsync(using context)
    val (execution, interrupt) = runner.unsafeRunAsyncAsInterruptibleFuture(effect)
    val held = try {
      Await.result(bodyEntered.future, Deadline)
      Await.result(runner.unsafeRunAsyncAsFuture(interrupt.interrupt), Deadline)
      Await.result(releaseEntered.future, Deadline)
      Await.result(runner.unsafeRunAsyncAsFuture(MiniBIOAsync.WeakAsyncForMiniBIOAsync.sleep(Observation)), Deadline)
      !execution.isCompleted && acquired.get() == 1 && released.get() == 0
    } finally {
      bodyGate.countDown()
      releaseGate.countDown()
      Await.result(incomingCompleted.future, Deadline)
      val _ = Await.result(execution, Deadline)
      context.close()
    }
    val exit = Await.result(execution, Deadline)
    println("CORE_IDENTITY_BRIDGE_FINALIZATION mode=" + interruptResult.name + " held=" + held + " acquired=" + acquired.get() + " released=" + released.get() + " interrupted=" + exit.isInterrupted + " requests=" + requests.get() + " callbacksDrained=true")
    Vector(
      "Identity bridge cancellation awaits its incoming held finalizer" -> held,
      "Identity bridge acquires and releases its resource once" -> (acquired.get() == 1 && released.get() == 1),
      "Identity bridge preserves semantic interruption" -> exit.isInterrupted,
      "Identity bridge requests incoming interruption once" -> (requests.get() == 1),
      "Identity bridge closes its owned executor after draining callbacks" -> true,
    ).map { case (name, passed) => (name + " " + interruptResult.name, passed) }
  }
}
