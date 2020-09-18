package izumi.functional.bio

import izumi.functional.bio.LowPriorityBIOForkInstances._BIOForkMonixBIO
import izumi.fundamentals.platform.language.unused
import monix.bio.IO
import zio.ZIO

trait BIOFork3[F[-_, +_, +_]] extends BIOForkInstances {
  def fork[R, E, A](f: F[R, E, A]): F[R, Nothing, BIOFiber3[F, E, A]]
}

private[bio] sealed trait BIOForkInstances

object BIOForkInstances extends LowPriorityBIOForkInstances {
  implicit val BIOForkZio: BIOFork3[ZIO] = new BIOFork3[ZIO] {
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

sealed trait LowPriorityBIOForkInstances extends LowPriorityBIOForkInstances1 {
  implicit def BIOForkMonix[BIOForkMonixBIO](implicit @unused M: _BIOForkMonixBIO[BIOForkMonixBIO]): BIOForkMonixBIO = {
    new BIOFork[monix.bio.IO] {
      override def fork[R, E, A](f: IO[E, A]): IO[Nothing, BIOFiber[monix.bio.IO, E, A]] = f.start.map(BIOFiber.fromMonix)
    }.asInstanceOf[BIOForkMonixBIO]
  }
}

object LowPriorityBIOForkInstances {
  final abstract class _BIOForkMonixBIO[A]
  object _BIOForkMonixBIO {
    @inline implicit final def get: _BIOForkMonixBIO[BIOFork[monix.bio.IO]] = null
  }
}

sealed trait LowPriorityBIOForkInstances1 {
  @inline implicit final def BIOFork3To2[FR[-_, +_, +_], R](implicit BIOFork3: BIOFork3[FR]): BIOFork[FR[R, +?, +?]] = cast3To2(BIOFork3)
}
