package izumi.functional.bio

import izumi.functional.bio.impl.BIOAsyncZio
import zio.ZIO
import zio.clock.Clock

import scala.concurrent.duration.{Duration, FiniteDuration}

trait BIOAsync[R[+ _, + _]] extends BIO[R] {
  final type Canceler = R[Nothing, Unit]

  @inline def async[E, A](register: (Either[E, A] => Unit) => Unit): R[E, A]

  @inline def asyncCancelable[E, A](register: (Either[E, A] => Unit) => Canceler): R[E, A]

  @inline def sleep(duration: Duration): R[Nothing, Unit]

  @inline def `yield`: R[Nothing, Unit]

  @inline def retryOrElse[A, E, A2 >: A, E2](r: R[E, A])(duration: FiniteDuration, orElse: => R[E2, A2]): R[E2, A2]

  @inline def timeout[E, A](r: R[E, A])(duration: Duration): R[E, Option[A]]

  @inline def race[E, A](r1: R[E, A])(r2: R[E ,A]): R[E, A]

  @inline def uninterruptible[E, A](r: R[E, A]): R[E, A]

  @inline def parTraverseN[E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => R[E, B]): R[E, List[B]]
}

object BIOAsync {
  def apply[R[+ _, + _] : BIOAsync]: BIOAsync[R] = implicitly

  implicit def BIOAsyncZio[R](implicit clockService: Clock): BIOAsync[ZIO[R, +?, +?]] = new BIOAsyncZio[R](clockService)
}
