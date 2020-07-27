package izumi.functional.bio

import zio.ZIO
import zio.internal.ZIOSucceedNow

trait BIOFork3[F[-_, +_, +_]] extends BIOForkInstances {
  def fork[R, E, A](f: F[R, E, A]): F[R, Nothing, BIOFiber3[F, E, A]]

  /** Race two actions, the winner is the first action to TERMINATE, whether by success or failure */
  def racePair[R, E, A, B](fa: F[R, E, A], fb: F[R, E, B]): F[R, E, Either[(A, BIOFiber3[F, E, B]), (BIOFiber3[F, E, A], B)]]
}

private[bio] sealed trait BIOForkInstances

object BIOForkInstances extends LowPriorityBIOForkInstances {
  implicit val BIOForkZio: BIOFork3[ZIO] = new BIOFork3[ZIO] {
    @inline override def fork[R, E, A](f: ZIO[R, E, A]): ZIO[R, Nothing, BIOFiber3[ZIO, E, A]] =
      f
        // FIXME: ZIO Bug / feature (interruption inheritance) breaks behavior in bracket/DIResource
        //  unless wrapped in `interruptible`
        //  see: https://github.com/zio/zio/issues/945
        .interruptible
        .forkDaemon
        .map(BIOFiber.fromZIO)

    @inline override final def racePair[R, E, A, B](
      r1: ZIO[R, E, A],
      r2: ZIO[R, E, B],
    ): ZIO[R, E, Either[(A, BIOFiber3[ZIO, E, B]), (BIOFiber3[ZIO, E, A], B)]] = {
      (r1.interruptible raceWith r2.interruptible)(
        { case (l, f) => l.fold(f.interrupt *> ZIO.halt(_), ZIOSucceedNow).map(lv => Left((lv, BIOFiber.fromZIO(f)))) },
        { case (r, f) => r.fold(f.interrupt *> ZIO.halt(_), ZIOSucceedNow).map(rv => Right((BIOFiber.fromZIO(f), rv))) },
      )
    }

  }
}

sealed trait LowPriorityBIOForkInstances {
  @inline implicit final def BIOFork3To2[FR[-_, +_, +_], R](implicit BIOFork3: BIOFork3[FR]): BIOFork[FR[R, +?, +?]] = cast3To2(BIOFork3)
}
