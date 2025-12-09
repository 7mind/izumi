package izumi.functional.bio.impl

import izumi.functional.bio.{Exit, UnsafeRun2}

import scala.annotation.unused
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.scalajs.js.timers

trait MiniBIOAsyncPlatformSpecific {

  protected def sleepImpl(duration: Duration): MiniBIOAsync[Nothing, Unit] = {
    duration match {
      case finite: FiniteDuration =>
        MiniBIOAsync.Async {
          (_, cb) =>
            timers.setTimeout(finite)(cb(Exit.Success(())))
            ()
        }
      case _ =>
        MiniBIOAsync.Async((_, _) => ())
    }
  }

  protected abstract class MiniBIOAsyncUnsafeRunPlatformSpecific(implicit @unused ec: ExecutionContext) extends UnsafeRun2[MiniBIOAsync]

}
