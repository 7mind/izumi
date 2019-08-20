package izumi.functional.bio

import zio.IO

trait BIOFork[F[_, _]] {
  def fork[E, A](f: F[E, A]): F[Nothing, BIOFiber[F, E, A]]
}

object BIOFork {
  def apply[F[_, _] : BIOFork]: BIOFork[F] = implicitly

  implicit object BIOForkZio extends BIOFork[IO] {
    override def fork[E, A](f: IO[E, A]): IO[Nothing, BIOFiber[IO, E, A]] =
      f.fork
        // FIXME: ZIO Bug / feature (interruption inheritance) breaks behavior in bracket/DIResource
        //  unless wrapped in `interruptible`
        //  see: https://github.com/zio/zio/issues/945
        .interruptible
        .map(BIOFiber.fromZIO)
  }
}
