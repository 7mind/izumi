package com.github.pshirshov.izumi.functional.bio

import scala.concurrent.duration.{Duration, FiniteDuration}

trait BIOAsync[R[+ _, + _]] extends BIO[R] with BIOAsyncInvariant[R] {
  override type Or[+E, +V] = R[E, V]
  override type Just[+V] = R[Nothing, V]

  @inline override def async[E, A](register: (Either[E, A] => Unit) => Unit): R[E, A]

  @inline override def sleep(duration: Duration): R[Nothing, Unit]

  @inline override def retryOrElse[A, E, A2 >: A, E2](r: R[E, A])(duration: FiniteDuration, orElse: => R[E2, A2]): R[E2, A2]
}

object BIOAsync {
  def apply[R[+ _, + _] : BIOAsync]: BIOAsync[R] = implicitly
}
