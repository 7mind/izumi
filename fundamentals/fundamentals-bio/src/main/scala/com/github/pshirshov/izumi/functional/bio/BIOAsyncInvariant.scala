package com.github.pshirshov.izumi.functional.bio

import scala.concurrent.duration.{Duration, FiniteDuration}

trait BIOAsyncInvariant[R[ _, _]] extends BIOInvariant[R] {
  @inline def async[E, A](register: (Either[E, A] => Unit) => Unit): R[E, A]

  @inline def sleep(duration: Duration): R[Nothing, Unit]

  @inline def `yield`: R[Nothing, Unit]

  @inline def retryOrElse[A, E, A2 >: A, E2](r: R[E, A])(duration: FiniteDuration, orElse: => R[E2, A2]): R[E2, A2]
}
