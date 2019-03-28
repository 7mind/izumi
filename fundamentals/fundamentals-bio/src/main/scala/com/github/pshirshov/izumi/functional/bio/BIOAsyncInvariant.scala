package com.github.pshirshov.izumi.functional.bio

import scala.concurrent.duration.{Duration, FiniteDuration}

trait BIOAsyncInvariant[R[ _, _]] extends BIOInvariant[R] {
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

object BIOAsyncInvariant {
  def apply[R[_, _]: BIOAsyncInvariant]: BIOAsyncInvariant[R] = implicitly
}
