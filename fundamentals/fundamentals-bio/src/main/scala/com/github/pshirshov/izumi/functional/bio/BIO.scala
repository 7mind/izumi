package com.github.pshirshov.izumi.functional.bio

import com.github.pshirshov.izumi.functional.bio.BIOExit.{Error, Success, Termination}
import scalaz.zio._
import scalaz.zio.duration.Duration.fromScala

import scala.concurrent.duration.{Duration, FiniteDuration}

trait BIO[R[+ _, + _]] extends BIOInvariant[R] {
  override type Or[+E, +V] = R[E, V]
  override type Just[+V] = R[Nothing, V]

  @inline override def map[E, A, B](r: R[E, A])(f: A => B): R[E, B]

  @inline override def point[V](v: => V): R[Nothing, V]

  @inline override def fail[E](v: E): R[E, Nothing]

  @inline override def terminate(v: Throwable): R[Nothing, Nothing]

  @inline override def redeem[E, A, E2, B](r: R[E, A])(err: E => R[E2, B], succ: A => R[E2, B]): R[E2, B]

  @inline override def now[A](a: A): R[Nothing, A]

  @inline override def syncThrowable[A](effect: => A): R[Throwable, A]

  @inline override def sync[A](effect: => A): R[Nothing, A]

  @inline override def flatMap[E, A, E1 >: E, B](r: R[E, A])(f0: A => R[E1, B]): R[E1, B]

  @inline override final val unit: R[Nothing, Unit] = now(())

  @inline override def void[E, A](r: R[E, A]): R[E, Unit]

  @inline override def leftMap[E, A, E2](r: R[E, A])(f: E => E2): R[E2, A]

  @inline override def bimap[E, A, E2, B](r: R[E, A])(f: E => E2, g: A => B): R[E2, B]

  @inline override def fromEither[E, V](v: Either[E, V]): R[E, V]

  @inline override def bracket[E, A, B](acquire: R[E, A])(release: A => R[Nothing, Unit])(use: A => R[E, B]): R[E, B]

  @inline override def sandboxWith[E, A, E2, B](r: R[E, A])(f: R[BIOExit.Failure[E], A] => R[BIOExit.Failure[E2], B]): R[E2, B]

  @inline override def sandbox[E, A](r: R[E, A]): R[BIOExit.Failure[E], A]
}


object BIO extends BIOSyntax {

  def apply[R[+ _, + _] : BIO]: BIO[R] = implicitly

  implicit object BIOZio extends BIOAsync[IO] {
    @inline override def bracket[E, A, B](acquire: IO[E, A])(release: A => IO[Nothing, Unit])(use: A => IO[E, B]): IO[E, B] =
      IO.bracket(acquire)(v => release(v))(use)

    @inline override def sleep(duration: Duration): IO[Nothing, Unit] = IO.sleep(fromScala(duration))

    @inline override def `yield`: IO[Nothing, Unit] = IO.yieldNow

    @inline override def sync[A](effect: => A): IO[Nothing, A] = IO.sync(effect)

    @inline override def now[A](a: A): IO[Nothing, A] = IO.succeed(a)

    @inline override def syncThrowable[A](effect: => A): IO[Throwable, A] = IO.syncThrowable(effect)

    @inline override def fromEither[L, R](v: Either[L, R]): IO[L, R] = IO.fromEither(v)

    @inline override def point[R](v: => R): IO[Nothing, R] = IO.succeedLazy(v)

    @inline override def void[E, A](r: IO[E, A]): IO[E, Unit] = r.void

    @inline override def terminate(v: Throwable): IO[Nothing, Nothing] = IO.die(v)

    @inline override def fail[E](v: E): IO[E, Nothing] = IO.fail(v)

    @inline override def map[E, A, B](r: IO[E, A])(f: A => B): IO[E, B] = r.map(f)

    @inline override def leftMap[E, A, E2](r: IO[E, A])(f: E => E2): IO[E2, A] = r.mapError(f)

    @inline override def bimap[E, A, E2, B](r: IO[E, A])(f: E => E2, g: A => B): IO[E2, B] = r.bimap(f, g)

    @inline override def flatMap[E, A, E1 >: E, B](r: IO[E, A])(f0: A => IO[E1, B]): IO[E1, B] = r.flatMap(f0)

    @inline override def redeem[E, A, E2, B](r: IO[E, A])(err: E => IO[E2, B], succ: A => IO[E2, B]): IO[E2, B] = r.redeem(err, succ)

    @inline override def widen[E, A, E1 >: E, A1 >: A](r: IO[E, A]): IO[E1, A1] = r

    @inline override def retryOrElse[A, E, A2 >: A, E2](r: IO[E, A])(duration: FiniteDuration, orElse: => IO[E2, A2]): IO[E2, A2] =
      r.retryOrElse(Schedule.duration(fromScala(duration)), {
        (_: Any, _: Any) =>
          orElse
      })

    @inline override def timeout[E, A](r: IO[E, A])(duration: Duration): IO[E, Option[A]] = r.timeout(fromScala(duration))

    @inline override def race[E, A](r1: IO[E, A])(r2: IO[E, A]): IO[E, A] = r1.race(r2)

    @inline override def traverse[E, A, B](l: Iterable[A])(f: A => IO[E, B]): IO[E, List[B]] = IO.foreach(l)(f)

    @inline override def sandboxWith[E, A, E2, B](r: IO[E, A])(f: IO[BIOExit.Failure[E], A] => IO[BIOExit.Failure[E2], B]): IO[E2, B] = {
      r.sandboxWith(r => f(r.mapError(toBIOExit[E])).mapError(failureToCause[E2]))
    }

    @inline override def sandbox[E, A](r: IO[E, A]): IO[BIOExit.Failure[E], A] = r.sandbox.mapError(toBIOExit[E])

    @inline override def async[E, A](register: (Either[E, A] => Unit) => Unit): IO[E, A] = {
      IO.async[E, A] {
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
      IO.asyncInterrupt[E, A] {
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

    @inline def toBIOExit[E, A](result: Exit[E, A]): BIOExit[E, A] = result match {
      case Exit.Success(v) =>
        Success(v)
      case Exit.Failure(cause) =>
        toBIOExit(cause)
    }

    @inline def toBIOExit[E](result: Exit.Cause[E]): BIOExit.Failure[E] = {
      result.failureOrCause match {
        case Left(err) =>
          Error(err)
        case Right(cause) =>
          val unchecked = cause.defects
          val exceptions = if (cause.interrupted) {
            new InterruptedException :: unchecked
          } else {
            unchecked
          }
          val compound = (exceptions: @unchecked) match {
            case e :: Nil => e
            case _ => FiberFailure(cause)
          }
          Termination(compound, exceptions)
      }
    }

    @inline def failureToCause[E](errEither: BIOExit.Failure[E]): Exit.Cause[E] = {
      errEither match {
        case Error(err) =>
          Exit.Cause.Fail(err)
        case Termination(_, Nil) =>
          Exit.Cause.Die(new IllegalArgumentException(s"Unexpected empty cause list from sandboxWith: $errEither"))
        case Termination(_, exceptions) =>
          exceptions.map {
            case _: InterruptedException => Exit.Cause.Interrupt
            case e => Exit.Cause.Die(e)
          }.reduce(_ ++ _)
      }
    }

  }

}

