package izumi.functional.bio.impl

import izumi.functional.bio.{Exit, UnsafeRun2}

import scala.annotation.unused
import scala.concurrent.ExecutionContext

trait MiniBIOPlatformSpecific {

  protected abstract class MiniBIOUnsafeRun2UnsafeRunSyncPlatformSpecific(implicit @unused ec: ExecutionContext) extends UnsafeRun2[MiniBIO] {

    override def unsafeRun[E, A](io: => MiniBIO[E, A]): A = {
      unsafeRunSync(io) match {
        case Exit.Success(value) => value
        case failure: Exit.Failure[E] => throw failure.trace.unsafeAttachTraceOrReturnNewThrowable()
      }
    }

    override def unsafeRunSync[E, A](io: => MiniBIO[E, A]): Exit[E, A] = {
      io.run()
    }

  }

}
