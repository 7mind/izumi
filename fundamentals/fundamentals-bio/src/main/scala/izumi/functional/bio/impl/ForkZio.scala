package izumi.functional.bio.impl

import izumi.functional.bio.Exit.ZIOExit
import izumi.functional.bio.{Fiber2, Fiber3, Fork3}
import zio.{IO, ZIO}

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.ExecutionContext

object ForkZio extends ForkZio

open class ForkZio extends Fork3[ZIO] {

  override def fork[R, E, A](f: ZIO[R, E, A]): ZIO[R, Nothing, Fiber2[IO, E, A]] = {
    val interrupted = new AtomicBoolean(true) // fiber could be interrupted before executing a single op
    ZIOExit
      .ZIOSignalOnNoExternalInterruptFailure {
        // FIXME: ZIO Bug / feature (interruption inheritance) breaks behavior in bracket/Lifecycle
        //  unless wrapped in `interruptible`
        //  see: https://github.com/zio/zio/issues/945
        f.interruptible
      }(ZIO.succeed(interrupted.set(true)))
      .forkDaemon
      .map(Fiber2.fromZIO(ZIO.succeed(interrupted.get())))
  }

  override def forkOn[R, E, A](ec: ExecutionContext)(f: ZIO[R, E, A]): ZIO[R, Nothing, Fiber3[ZIO, E, A]] = {
    val interrupted = new AtomicBoolean(true) // fiber could be interrupted before executing a single op
    ZIOExit
      .ZIOSignalOnNoExternalInterruptFailure {
        // FIXME: ZIO Bug / feature (interruption inheritance) breaks behavior in bracket/Lifecycle
        //  unless wrapped in `interruptible`
        //  see: https://github.com/zio/zio/issues/945
        f.interruptible
          .onExecutionContext(ec)
      }(ZIO.succeed(interrupted.set(true)))
      .forkDaemon
      .map(Fiber2.fromZIO(ZIO.succeed(interrupted.get())))
  }

}
