package izumi.functional.bio.impl

import izumi.functional.bio.{Fiber2, Fork3}
import zio.{IO, ZIO}

object ForkZio extends ForkZio

class ForkZio extends Fork3[ZIO] {
  override def fork[R, E, A](f: ZIO[R, E, A]): ZIO[R, Nothing, Fiber2[IO, E, A]] = {
    f
      // FIXME: ZIO Bug / feature (interruption inheritance) breaks behavior in bracket/Lifecycle
      //  unless wrapped in `interruptible`
      //  see: https://github.com/zio/zio/issues/945
      .interruptible.forkDaemon
      .map(Fiber2.fromZIO)
  }
}
