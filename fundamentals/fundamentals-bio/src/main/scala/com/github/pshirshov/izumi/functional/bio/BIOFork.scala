package com.github.pshirshov.izumi.functional.bio

import scalaz.zio.IO

trait BIOFork[R[_, _]] {
  def fork[E, A](f: R[E, A]): R[Nothing, BIOFiber[R, E, A]]
}

object BIOFork {
  def apply[R[_, _] : BIOFork]: BIOFork[R] = implicitly

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
