package izumi.functional.bio.impl

import izumi.functional.bio.{Fiber3, Fork3}
import zio.ZIO

object ForkZio extends ForkZio

class ForkZio extends Fork3[ZIO] {
  override def fork[R, E, A](f: ZIO[R, E, A]): ZIO[R, Nothing, Fiber3[ZIO, E, A]] = {
    f
      // FIXME: ZIO Bug / feature (interruption inheritance) breaks behavior in bracket/Lifecycle
      //  unless wrapped in `interruptible`
      //  see: https://github.com/zio/zio/issues/945
      .interruptible.forkDaemon
      .map(Fiber3.fromZIO)
  }
}
