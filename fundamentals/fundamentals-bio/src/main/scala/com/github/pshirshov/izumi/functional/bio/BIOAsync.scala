package com.github.pshirshov.izumi.functional.bio

import scalaz.zio.clock.Clock
import scalaz.zio.duration.Duration.fromScala
import scalaz.zio.{IO, ZIO, ZSchedule}

import scala.concurrent.duration.{Duration, FiniteDuration}

trait BIOAsync[R[+ _, + _]] extends BIO[R] with BIOAsyncInvariant[R] {
  override type Or[+E, +V] = R[E, V]
  override type Just[+V] = R[Nothing, V]

  @inline override def async[E, A](register: (Either[E, A] => Unit) => Unit): R[E, A]

  @inline override def asyncCancelable[E, A](register: (Either[E, A] => Unit) => Canceler): R[E, A]

  @inline override def sleep(duration: Duration): R[Nothing, Unit]

  @inline override def `yield`: R[Nothing, Unit]

  @inline override def retryOrElse[A, E, A2 >: A, E2](r: R[E, A])(duration: FiniteDuration, orElse: => R[E2, A2]): R[E2, A2]

  @inline override def timeout[E, A](r: R[E, A])(duration: Duration): R[E, Option[A]]

  @inline override def race[E, A](r1: R[E, A])(r2: R[E ,A]): R[E, A]

  @inline override def uninterruptible[E, A](r: R[E, A]): R[E, A]

  @inline override def parTraverseN[E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => R[E, B]): R[E, List[B]]
}

object BIOAsync {
  def apply[R[+ _, + _] : BIOAsync]: BIOAsync[R] = implicitly

  implicit def BIOAsyncZio(implicit clockService: Clock): BIOAsync[IO] = new BIO.BIOZio with BIOAsync[IO] {

    @inline override def `yield`: IO[Nothing, Unit] = IO.yieldNow

    @inline override def sleep(duration: Duration): IO[Nothing, Unit] = ZIO.sleep(fromScala(duration)).provide(clockService)

    @inline override def retryOrElse[A, E, A2 >: A, E2](r: IO[E, A])(duration: FiniteDuration, orElse: => IO[E2, A2]): IO[E2, A2] =
      r.retryOrElse(ZSchedule.duration(fromScala(duration)), {
        (_: Any, _: Any) =>
          orElse
      }).provide(clockService)

    @inline override def timeout[E, A](r: IO[E, A])(duration: Duration): IO[E, Option[A]] = r.timeout(fromScala(duration)).provide(clockService)

    @inline override def race[E, A](r1: IO[E, A])(r2: IO[E, A]): IO[E, A] = r1.race(r2)

    @inline override def async[E, A](register: (Either[E, A] => Unit) => Unit): IO[E, A] = {
      IO.effectAsync[E, A] {
        cb =>
          register {
            case Right(v) =>
              cb(IO.succeed(v))
            case Left(t) =>
              cb(IO.fail(t))
          }
      }
    }

    @inline override def asyncCancelable[E, A](register: (Either[E, A] => Unit) => Canceler): IO[E, A] = {
      IO.effectAsyncInterrupt {
        cb =>
          val canceler = register {
            case Right(v) =>
              cb(IO.succeed(v))
            case Left(t) =>
              cb(IO.fail(t))
          }
          Left(canceler)
      }
    }

    @inline override def uninterruptible[E, A](r: IO[E, A]): IO[E, A] = {
      r.uninterruptible
    }

    @inline override def parTraverseN[E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => IO[E, B]): IO[E, List[B]] = {
      IO.foreachParN(maxConcurrent.toLong)(l)(f)
    }

  }
}
