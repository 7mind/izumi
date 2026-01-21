package izumi.functional.bio.impl

import izumi.functional.bio.UnsafeRun2.NamedThreadFactory
import izumi.functional.bio.{Exit, UnsafeRun2}

import java.util.concurrent.{ScheduledExecutorService, ScheduledThreadPoolExecutor, TimeUnit}
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext}

trait MiniBIOAsyncPlatformSpecific {

  private[impl] lazy val scheduler: ScheduledExecutorService = {
    val executor = new ScheduledThreadPoolExecutor(1, new NamedThreadFactory("MiniBIOAsync-timer", true, Some(Thread.MAX_PRIORITY)))
    executor.setRemoveOnCancelPolicy(true)
    executor
  }

  protected def sleepImpl(duration: Duration): MiniBIOAsync[Nothing, Unit] = {
    duration match {
      case finite: FiniteDuration =>
        MiniBIOAsync.Async {
          (_, cb) =>
            scheduler.schedule(
              (() => cb(Exit.Success(()))): Runnable,
              finite.toNanos,
              TimeUnit.NANOSECONDS,
            )
            ()
        }
      case _ =>
        MiniBIOAsync.Async((_, _) => ())
    }
  }

  protected abstract class MiniBIOAsyncUnsafeRunPlatformSpecific(implicit ec: ExecutionContext) extends UnsafeRun2[MiniBIOAsync] {

    override final def unsafeRun[E, A](io: => MiniBIOAsync[E, A]): A = {
      unsafeRunSync(io) match {
        case Exit.Success(value) => value
        case failure: Exit.Failure[E] => throw failure.trace.unsafeAttachTraceOrReturnNewThrowable()
      }
    }

    override final def unsafeRunSync[E, A](io: => MiniBIOAsync[E, A]): Exit[E, A] = {
      val asyncInterrupt = new MiniBIOAsync.AsyncInterruptRef
      io.runSyncToFirstAsyncBoundaryInterruptible(asyncInterrupt) match {
        case Left(exit) => exit
        case Right(continuation) =>
          try {
            Await.result(continuation(ec), Duration.Inf)
          } catch {
            case interrupted: InterruptedException =>
              asyncInterrupt.interrupt()
              Exit.Termination.forThrowable(interrupted)
          }
      }
    }

  }

}
