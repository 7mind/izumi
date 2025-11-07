package izumi.functional.bio.impl

import izumi.functional.bio.{Exit, UnsafeRun2}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

trait MiniBIOAsyncPlatformSpecific {

  protected abstract class MiniBIOAsyncUnsafeRun2UnsafeRunSyncPlatformSpecific(implicit ec: ExecutionContext) extends UnsafeRun2[MiniBIOAsync] {

    override def unsafeRun[E, A](io: => MiniBIOAsync[E, A]): A = {
      unsafeRunSync(io) match {
        case Exit.Success(value) => value
        case failure: Exit.Failure[E] => throw failure.trace.unsafeAttachTraceOrReturnNewThrowable()
      }
    }

    override def unsafeRunSync[E, A](io: => MiniBIOAsync[E, A]): Exit[E, A] = {
      io.runSyncToFirstAsyncBoundary() match {
        case Left(exit) => exit
        case Right(continuation) =>
          Await.result(continuation(ec), Duration.Inf)
      }
    }

  }

}
