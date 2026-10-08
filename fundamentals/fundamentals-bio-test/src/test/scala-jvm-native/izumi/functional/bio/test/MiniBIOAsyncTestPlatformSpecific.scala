package izumi.functional.bio.test

import izumi.functional.bio.impl.MiniBIOAsync
import izumi.functional.bio.Exit
import izumi.fundamentals.testkit.AsyncWordSpec

import java.util.concurrent.{Executors, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext, Promise}

trait MiniBIOAsyncTestPlatformSpecific extends AsyncWordSpec {
  val parallelEc: ExecutionContext = {
    ExecutionContext.fromExecutor(Executors.newCachedThreadPool())
  }

  "MiniBIOAsync" should {
    "deliver a request made during synchronous execution before successful completion" in {
      checkTerminalRequest(fails = false)
    }

    "retain an independent synchronous failure after a pending request" in {
      checkTerminalRequest(fails = true)
    }
  }

  private final val TerminalRequestTimeout = 2.seconds

  private def checkTerminalRequest(fails: Boolean) = {
    val F = MiniBIOAsync.WeakAsyncForMiniBIOAsync
    val entered = Promise[Unit]()
    val gate = Promise[Unit]()
    val completed = new AtomicInteger(0)
    val original = new IllegalStateException("independent terminal failure")
    val effect = F.flatMap(F.fromFuture(_ => scala.concurrent.Future.successful(()))) { _ =>
      F.syncThrowable {
        entered.success(())
        Await.result(gate.future, Duration.Inf)
        completed.incrementAndGet()
        if (fails) throw original
        42
      }
    }
    val runner = MiniBIOAsync.UnsafeRunMiniBIOAsync(using parallelEc)
    val (future, interrupt) = runner.unsafeRunAsyncAsInterruptibleFuture(effect)
    val result = for {
      _ <- withTimeout(entered.future, TerminalRequestTimeout)
      _ <- interrupt.interrupt.runOnEC(executionContext)
      _ = gate.success(())
      exit <- withTimeout(future, TerminalRequestTimeout)
    } yield {
      assert(completed.get() == 1)
      if (fails) {
        exit match {
          case Exit.Error(error, _) => assert(error eq original)
          case other => fail(s"Expected independent terminal failure, got $other")
        }
      } else {
        assert(exit.isInterrupted)
      }
    }
    result.andThen { case _ => gate.trySuccess(()); () }
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
