package com.github.pshirshov.izumi.functional.bio

import com.github.pshirshov.izumi.functional.bio.impl.BIOAsyncZio
import zio.ZIO
import zio.clock.Clock

import scala.concurrent.duration.{Duration, FiniteDuration}

trait BIOAsync[F[+_, +_]] extends BIO[F] with BIOAsyncInstances {
  final type Canceler = F[Nothing, Unit]

  @inline def async[E, A](register: (Either[E, A] => Unit) => Unit): F[E, A]

  @inline def asyncCancelable[E, A](register: (Either[E, A] => Unit) => Canceler): F[E, A]

  @inline def sleep(duration: Duration): F[Nothing, Unit]

  @inline def `yield`: F[Nothing, Unit]

  @inline def retryOrElse[A, E, A2 >: A, E2](r: F[E, A])(duration: FiniteDuration, orElse: => F[E2, A2]): F[E2, A2]

  @inline def timeout[E, A](r: F[E, A])(duration: Duration): F[E, Option[A]]

  @inline def race[E, A](r1: F[E, A])(r2: F[E ,A]): F[E, A]

  @inline def uninterruptible[E, A](r: F[E, A]): F[E, A]

  @inline def parTraverseN[E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => F[E, B]): F[E, List[B]]
}

sealed trait BIOAsyncInstances
object BIOAsyncInstances {
  implicit def BIOAsyncZio[F](implicit clockService: Clock): BIOAsync[ZIO[F, +?, +?]] = new BIOAsyncZio[F](clockService)
}
