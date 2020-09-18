package izumi.functional.bio.impl

import izumi.functional.bio.{BIOFiber3, BIOFork3}
import zio.ZIO

object BIOForkZIO extends BIOForkZIO

class BIOForkZIO extends BIOFork3[ZIO] {
  override def fork[R, E, A](f: ZIO[R, E, A]): ZIO[R, Nothing, BIOFiber3[ZIO, E, A]] = {
    f
      // FIXME: ZIO Bug / feature (interruption inheritance) breaks behavior in bracket/DIResource
      //  unless wrapped in `interruptible`
      //  see: https://github.com/zio/zio/issues/945
      .interruptible
      .forkDaemon
      .map(BIOFiber3.fromZIO)
  }
}
