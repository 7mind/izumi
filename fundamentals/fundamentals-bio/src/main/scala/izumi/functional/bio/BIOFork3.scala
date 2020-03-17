package izumi.functional.bio

import zio.ZIO

trait BIOFork3[F[-_, +_, +_]] extends BIOForkInstances {
  def fork[R, E, A](f: F[R, E, A]): F[R, Nothing, BIOFiber3[F, E, A]]
}

private[bio] sealed trait BIOForkInstances
object BIOForkInstances {
  // FIXME: bad encoding for lifting to 2-parameters...
  implicit def BIOForkZioIO[R]: BIOFork[ZIO[R, +?, +?]] = BIOForkZio.asInstanceOf[BIOFork[ZIO[R, +?, +?]]]

  implicit object BIOForkZio extends BIOFork3[ZIO] {
    override def fork[R, E, A](f: ZIO[R, E, A]): ZIO[R, Nothing, BIOFiber3[ZIO, E, A]] =
      f
      // FIXME: ZIO Bug / feature (interruption inheritance) breaks behavior in bracket/DIResource
      //  unless wrapped in `interruptible`
      //  see: https://github.com/zio/zio/issues/945
      .interruptible
        .forkDaemon
        .map(BIOFiber.fromZIO)
  }
}
