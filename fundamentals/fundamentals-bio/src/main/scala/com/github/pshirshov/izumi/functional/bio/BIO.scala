package com.github.pshirshov.izumi.functional.bio

import scalaz.zio.{IO, Schedule}

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.language.{higherKinds, implicitConversions}

trait BIOInvariant[R[_, _]] {
  type Or[E, V] = R[E, V]
  type Just[V] = R[Nothing, V]

  @inline def map[E, A, B](r: R[E, A])(f: A => B): R[E, B]

  @inline def redeem[E, A, E2, B](r: R[E, A])(err: E => R[E2, B], succ: A => R[E2, B]): R[E2, B]

  @inline def syncThrowable[A](effect: => A): R[Throwable, A]

  @inline def flatMap[E, A, E1 >: E, B](r: R[E, A])(f0: A => R[E1, B]): R[E1, B]

  @inline def void[E, A](r: R[E, A]): R[E, Unit]

  @inline def leftMap[E, A, E2](r: R[E, A])(f: E => E2): R[E2, A]

  @inline def bimap[E, A, E2, B](r: R[E, A])(f: E => E2, g: A => B): R[E2, B]

  @inline def fromEither[E, V](v: => Either[E, V]): R[E, V]

  @inline def sync[A](effect: => A): R[Nothing, A]

  @inline def point[V](v: => V): R[Nothing, V]

  @inline def fail[E](v: => E): R[E, Nothing]

  @inline def terminate(v: => Throwable): R[Nothing, Nothing]

  @inline def maybe[V](v: => Either[Throwable, V]): R[Nothing, V] = {
    v match {
      case Left(f) =>
        terminate(f).asInstanceOf[R[Nothing, V]]
      case Right(r) =>
        point(r)
    }
  }

  @inline def now[A](a: A): R[Nothing, A]

  @inline def unit: R[Nothing, Unit] = now(())

  @inline def bracket[E, A, B](acquire: R[E, A])(release: A => R[Nothing, Unit])(use: A => R[E, B]): R[E, B]

  @inline def sandboxWith[E, A, E2, B](r: R[E, A])(f: R[Either[List[Throwable], E], A] => R[Either[List[Throwable], E2], B]): R[E2, B]

  @inline def widen[E, A, E1 >: E, A1 >: A](r: R[E, A]): R[E1, A1]
}

trait BIOAsyncInvariant[R[ _, _]] extends BIOInvariant[R] {
  @inline def sleep(duration: Duration): R[Nothing, Unit]

  @inline def retryOrElse[A, E, A2 >: A, E2](r: R[E, A])(duration: FiniteDuration, orElse: => R[E2, A2]): R[E2, A2]
}

trait BIO[R[+ _, + _]] extends BIOInvariant[R] {
  override type Or[+E, +V] = R[E, V]
  override type Just[+V] = R[Nothing, V]

  @inline override def map[E, A, B](r: R[E, A])(f: A => B): R[E, B]

  @inline override def point[V](v: => V): R[Nothing, V]

  @inline override def fail[E](v: => E): R[E, Nothing]

  @inline override def terminate(v: => Throwable): R[Nothing, Nothing]

  @inline override def redeem[E, A, E2, B](r: R[E, A])(err: E => R[E2, B], succ: A => R[E2, B]): R[E2, B]

  @inline override def maybe[V](v: => Either[Throwable, V]): R[Nothing, V] = {
    v match {
      case Left(f) =>
        terminate(f).asInstanceOf[R[Nothing, V]]
      case Right(r) =>
        point(r)
    }
  }

  @inline override def now[A](a: A): R[Nothing, A]

  @inline override def syncThrowable[A](effect: => A): R[Throwable, A]

  @inline override def sync[A](effect: => A): R[Nothing, A]

  @inline override def flatMap[E, A, E1 >: E, B](r: R[E, A])(f0: A => R[E1, B]): R[E1, B]

  override final val unit: R[Nothing, Unit] = now(())

  @inline override def void[E, A](r: R[E, A]): R[E, Unit]

  @inline override def leftMap[E, A, E2](r: R[E, A])(f: E => E2): R[E2, A]

  @inline override def bimap[E, A, E2, B](r: R[E, A])(f: E => E2, g: A => B): R[E2, B]

  @inline override def fromEither[E, V](v: => Either[E, V]): R[E, V]

  @inline override def bracket[E, A, B](acquire: R[E, A])(release: A => R[Nothing, Unit])(use: A => R[E, B]): R[E, B]

  @inline override def sandboxWith[E, A, E2, B](r: R[E, A])(f: R[Either[List[Throwable], E], A] => R[Either[List[Throwable], E2], B]): R[E2, B]
}

trait BIOAsync[R[+ _, + _]] extends BIO[R] with BIOAsyncInvariant[R] {
  override type Or[+E, +V] = R[E, V]
  override type Just[+V] = R[Nothing, V]

  @inline override def sleep(duration: Duration): R[Nothing, Unit]

  @inline override def retryOrElse[A, E, A2 >: A, E2](r: R[E, A])(duration: FiniteDuration, orElse: => R[E2, A2]): R[E2, A2]
}

object BIOAsync {
  def apply[R[+ _, + _] : BIOAsync]: BIOAsync[R] = implicitly

}

trait BIOSyntax {

  @inline implicit def ToOps[R[+ _, + _] : BIO, E, A](self: R[E, A]): BIOSyntax.BIOOps[R, E, A] = new BIOSyntax.BIOOps[R, E, A](self)

  @inline implicit def ToAsyncOps[R[+ _, + _] : BIOAsync, E, A](self: R[E, A]): BIOSyntax.BIOAsyncOps[R, E, A] = new BIOSyntax.BIOAsyncOps[R, E, A](self)

  @inline implicit def ToFlattenOps[R[+ _, + _] : BIO, E, A](self: R[E, R[E, A]]): BIOSyntax.BIOFlattenOps[R, E, A] = new BIOSyntax.BIOFlattenOps[R, E, A](self)

}

object BIOSyntax {

  final class BIOOps[R[+ _, + _], E, A](private val r: R[E, A])(implicit private val R: BIO[R]) {
    @inline def map[B](f: A => B): R[E, B] = R.map(r)(f)

    @inline def as[B](b: B): R[E, B] = R.map(r)(_ => b)

    @inline def leftMap[E2](f: E => E2): R[E2, A] = R.leftMap(r)(f)

    @inline def bimap[E2, B](f: E => E2, g: A => B): R[E2, B] = R.bimap(r)(f, g)

    @inline def flatMap[E1 >: E, B](f0: A => R[E1, B]): R[E1, B] = R.flatMap[E, A, E1, B](r)(f0)

    @inline def *>[E1 >: E, B](f0: => R[E1, B]): R[E1, B] = R.flatMap[E, A, E1, B](r)(_ => f0)

    @inline def redeem[E2, B](err: E => R[E2, B], succ: A => R[E2, B]): R[E2, B] = R.redeem[E, A, E2, B](r)(err, succ)

    @inline def redeemPure[E2, B](err: E => B, succ: A => B): R[E2, B] =
      redeem(err.andThen(R.now), succ.andThen(R.now))

    @inline def sandboxWith[E2, B](f: R[Either[List[Throwable], E], A] => R[Either[List[Throwable], E2], B]): R[E2, B] = R.sandboxWith(r)(f)


    @inline def void: R[E, Unit] = R.void(r)

    @inline def catchAll[E2, A2 >: A](h: E => R[E2, A2]): R[E2, A2] = R.redeem(r)(h, R.now)
  }

  final class BIOAsyncOps[R[+ _, + _], E, A](private val r: R[E, A])(implicit private val R: BIOAsync[R]) {
    @inline def retryOrElse[A2 >: A, E2](duration: FiniteDuration, orElse: => R[E2, A2]): R[E2, A2] = R.retryOrElse[A, E, A2, E2](r)(duration, orElse)
  }

  final class BIOFlattenOps[R[+ _, + _], E, A](private val r: R[E, R[E, A]])(implicit private val R: BIO[R]) {
    @inline def flatten: R[E, A] = R.flatMap(r)(a => a)
  }

}

object BIO extends BIOSyntax {

  def apply[R[+ _, + _] : BIO]: BIO[R] = implicitly

  implicit object BIOZio extends BIOAsync[IO] {
    @inline override def bracket[E, A, B](acquire: IO[E, A])(release: A => IO[Nothing, Unit])(use: A => IO[E, B]): IO[E, B] =
      IO.bracket(acquire)(v => release(v))(use)

    @inline override def sleep(duration: Duration): IO[Nothing, Unit] = IO.sleep(duration)

    @inline override def sync[A](effect: => A): IO[Nothing, A] = IO.sync(effect)

    @inline override def now[A](a: A): IO[Nothing, A] = IO.now(a)

    @inline override def syncThrowable[A](effect: => A): IO[Throwable, A] = IO.syncThrowable(effect)

    @inline override def fromEither[L, R](v: => Either[L, R]): IO[L, R] = IO.fromEither(v)

    @inline override def point[R](v: => R): IO[Nothing, R] = IO.point(v)

    @inline override def void[E, A](r: IO[E, A]): IO[E, Unit] = r.void

    @inline override def terminate(v: => Throwable): IO[Nothing, Nothing] = IO.terminate(v)

    @inline override def fail[E](v: => E): IO[E, Nothing] = IO.fail(v)

    @inline override def map[E, A, B](r: IO[E, A])(f: A => B): IO[E, B] = r.map(f)

    @inline override def leftMap[E, A, E2](r: IO[E, A])(f: E => E2): IO[E2, A] = r.leftMap(f)

    @inline override def bimap[E, A, E2, B](r: IO[E, A])(f: E => E2, g: A => B): IO[E2, B] = r.bimap(f, g)

    @inline override def flatMap[E, A, E1 >: E, B](r: IO[E, A])(f0: A => IO[E1, B]): IO[E1, B] = r.flatMap(f0)

    @inline override def redeem[E, A, E2, B](r: IO[E, A])(err: E => IO[E2, B], succ: A => IO[E2, B]): IO[E2, B] = r.redeem(err, succ)

    @inline override def sandboxWith[E, A, E2, B](r: IO[E, A])(f: IO[Either[List[Throwable], E], A] => IO[Either[List[Throwable], E2], B]): IO[E2, B] =
      r.sandboxWith(f)

    @inline override def retryOrElse[A, E, A2 >: A, E2](r: IO[E, A])(duration: FiniteDuration, orElse: => IO[E2, A2]): IO[E2, A2] =
      r.retryOrElse[A2, Any, Duration, E2](Schedule.duration(duration), {
        (_: Any, _: Any) =>
          orElse
      })

    @inline override def widen[E, A, E1 >: E, A1 >: A](r: IO[E, A]): IO[E1, A1] = r
  }
}





