package izumi.functional.bio.impl

import izumi.functional.bio.{Exit, UnsafeRun2}

trait MiniBIOPlatformSpecific {

  protected abstract class MiniBIOUnsafeRunPlatformSpecific extends UnsafeRun2[MiniBIO] {

    override final def unsafeRun[E, A](io: => MiniBIO[E, A]): A = {
      unsafeRunSync(io) match {
        case Exit.Success(value) => value
        case failure: Exit.Failure[E] => throw failure.trace.unsafeAttachTraceOrReturnNewThrowable()
      }
    }

    override final def unsafeRunSync[E, A](io: => MiniBIO[E, A]): Exit[E, A] = {
      io.run()
    }

  }

}
