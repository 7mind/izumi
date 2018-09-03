package com.github.pshirshov.izumi.idealingua.runtime.bio

import scalaz.zio.{ExitResult, IO, Schedule}

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.language.{higherKinds, implicitConversions}

trait MicroBIO[R[_, _]] {
  type Or[E, V] = R[E, V]
  type Just[V] = R[Nothing, V]

  @inline def map[E, A, B](r: R[E, A])(f: A => B): R[E, B]

  @inline def point[V](v: => V): R[Nothing, V]

  @inline def fail[E](v: => E): R[E, Nothing]

  @inline def terminate(v: => Throwable): R[Nothing, Nothing]

  @inline def redeem[E, A, E2, B](r: R[E, A])(err: E => R[E2, B], succ: A => R[E2, B]): R[E2, B]

  @inline def maybe[V](v: => Either[Throwable, V]): R[Nothing, V] = {
    v match {
      case Left(f) =>
        terminate(f).asInstanceOf[R[Nothing, V]]
      case Right(r) =>
        point(r)
    }
  }
}

trait BIO[R[+_, +_]] extends MicroBIO[R] {
  override type Or[+E, +V] = R[E, V]
  override type Just[+V] = R[Nothing, V]

  @inline def now[A](a: A): R[Nothing, A]

  @inline def syncThrowable[A](effect: => A): R[Throwable, A]

  @inline def sync[A](effect: => A): R[Nothing, A]

  @inline def flatMap[E, A, E1 >: E, B](r: R[E, A])(f0: A => R[E1, B]): R[E1, B]

  final val unit: R[Nothing, Unit] = now(())

  @inline def void[E, A](r: R[E, A]): R[E, Unit]

  @inline def leftMap[E, A, E2](r: R[E, A])(f: E => E2): R[E2, A]

  @inline def bimap[E, A, E2, B](r: R[E, A])(f: E => E2, g: A => B): R[E2, B]

  @inline def fromEither[E, V](v: => Either[E, V]): R[E, V]

  //
  @inline def sleep(duration: Duration): R[Nothing, Unit]

  @inline def bracket0[E, A, B](acquire: R[E, A])(release: A => R[Nothing, Unit])(use: A => R[E, B]): R[E, B]

  @inline def sandboxWith[E, A, E2, B](r: R[E, A])(f: R[Either[List[Throwable], E], A] => R[Either[List[Throwable], E2], B]): R[E2, B]

  @inline def retryOrElse[A, E, A2 >: A, E2](r: R[E, A])(duration: FiniteDuration, orElse: => R[E2, A2]): R[E2, A2]
}

trait BIOSyntax {

  @inline implicit def ToOps[R[+_, +_]: BIO, E, A](self: R[E, A]): BIOSyntax.BIOOps[R, E, A] = new BIOSyntax.BIOOps[R, E, A](self)

  @inline implicit def ToFlattenOps[R[+_, +_]: BIO, E, A](self: R[E, R[E, A]]): BIOSyntax.BIOFlattenOps[R, E, A] = new BIOSyntax.BIOFlattenOps[R, E, A](self)

}

object BIOSyntax {

  final class BIOOps[R[+_, + _], E, A](private val r: R[E, A])(implicit private val R: BIO[R]) {
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

    @inline def retryOrElse[A2 >: A, E2](duration: FiniteDuration, orElse: => R[E2, A2]): R[E2, A2] = R.retryOrElse[A, E, A2, E2](r)(duration, orElse)

    @inline def void: R[E, Unit] = R.void(r)

    @inline def catchAll[E2, A2 >: A](h: E => R[E2, A2]): R[E2, A2] = R.redeem(r)(h, R.now)
  }

  final class BIOFlattenOps[R[+_, + _], E, A](private val r: R[E, R[E, A]])(implicit private val R: BIO[R]) {
    @inline def flatten: R[E, A] = R.flatMap(r)(a => a)
  }

}

object BIO extends BIOSyntax {

  def apply[R[+_, +_]: BIO]: BIO[R] = implicitly

  implicit object BIOZio extends BIO[IO] {
    @inline def bracket0[E, A, B](acquire: IO[E, A])(release: A => IO[Nothing, Unit])(use: A => IO[E, B]): IO[E, B] =
      IO.bracket0(acquire)((v, _: ExitResult[E, B]) => release(v))(use)

    @inline def sleep(duration: Duration): IO[Nothing, Unit] = IO.sleep(duration)

    @inline def sync[A](effect: => A): IO[Nothing, A] = IO.sync(effect)

    @inline def now[A](a: A): IO[Nothing, A] = IO.now(a)

    @inline def syncThrowable[A](effect: => A): IO[Throwable, A] = IO.syncThrowable(effect)

    @inline def fromEither[L, R](v: => Either[L, R]): IO[L, R] = IO.fromEither(v)

    @inline def point[R](v: => R): IO[Nothing, R] = IO.point(v)

    @inline def void[E, A](r: IO[E, A]): IO[E, Unit] = r.void

    @inline def terminate(v: => Throwable): IO[Nothing, Nothing] = IO.terminate(v)

    @inline def fail[E](v: => E): IO[E, Nothing] = IO.fail(v)

    @inline def map[E, A, B](r: IO[E, A])(f: A => B): IO[E, B] = r.map(f)

    @inline def leftMap[E, A, E2](r: IO[E, A])(f: E => E2): IO[E2, A] = r.leftMap(f)

    @inline def bimap[E, A, E2, B](r: IO[E, A])(f: E => E2, g: A => B): IO[E2, B] = r.bimap(f, g)

    @inline def flatMap[E, A, E1 >: E, B](r: IO[E, A])(f0: A => IO[E1, B]): IO[E1, B] = r.flatMap(f0)

    @inline def redeem[E, A, E2, B](r: IO[E, A])(err: E => IO[E2, B], succ: A => IO[E2, B]): IO[E2, B] = r.redeem(err, succ)

    @inline def sandboxWith[E, A, E2, B](r: IO[E, A])(f: IO[Either[List[Throwable], E], A] => IO[Either[List[Throwable], E2], B]): IO[E2, B] =
      r.sandboxWith(f)

    @inline def retryOrElse[A, E, A2 >: A, E2](r: IO[E, A])(duration: FiniteDuration, orElse: => IO[E2, A2]): IO[E2, A2] =
      r.retryOrElse[A2, Any, Duration, E2](Schedule.duration(duration), {
        (_: Any, _: Any) =>
          orElse
      })
  }

//    @deprecated("BIO<->EitherT adapter is not recommended to use", "")
//    implicit def EitherTResult extends IRTResult[EitherT[cats.effect.IO, ?, ?]] {
//      def ME[E]: MonadError[Or[E, ?], E] = implicitly
//
//      // this isn't nice
//      @inline def cancel[V](v: => Throwable): Just[V] = ME[Nothing].point(throw v)
//
//      @inline def choice[E, V](v: => Either[E, V]): Or[E, V] = v match {
//        case Right(r) =>
//          ME[E].pure(r)
//
//        case Left(l) =>
//          ME[E].raiseError(l)
//      }
//
//      @inline def just[V](v: => V): Just[V] = ME[Nothing].pure(v)
//
//      @inline def stop[E](v: => E): EitherTResult.Or[E, Nothing] = ME[E].raiseError(v)
//
//      @inline def map[E, A, B](r: EitherTResult.Or[E, A])(f: A => B): EitherTResult.Or[E, B] = r.map(f)
//
//      @inline def leftMap[E, A, E2](r: EitherTResult.Or[E, A])(f: E => E2): EitherTResult.Or[E2, A] = r.leftMap(f)
//
//      @inline def bimap[E, A, E2, B](r: EitherTResult.Or[E, A])(f: E => E2, g: A => B): EitherTResult.Or[E2, B] = r.bimap(f, g)
//
//      @inline def flatMap[E, A, E1 >: E, B](r: EitherTResult.Or[E, A])(f0: A => EitherT[cats.effect.IO, E1, B]): EitherT[cats.effect.IO, E1, B] = r.flatMap(f0)
//
//      override def fromEither[E, V](v: => Either[E, V]): EitherTResult.Or[E, V] = ???
//
//      override def point[V](v: => V): EitherTResult.Just[V] = ???
//
//      override def now[A](a: A): EitherTResult.Just[A] = ???
//
//      override def fail[E](v: => E): EitherTResult.Or[E, Nothing] = ???
//
//      override def terminate[V](v: => Throwable): EitherTResult.Just[V] = ???
//
//      override def syncThrowable[A](effect: => A): EitherTResult.Or[Throwable, A] = ???
//
//      override def sync[A](effect: => A): EitherTResult.Or[Nothing, A] = ???
//
//      override def bracket0[E, A, B](acquire: EitherT[cats.effect.IO, E, A])(release: A => EitherT[cats.effect.IO, Nothing, Unit])(use: A => EitherT[cats.effect.IO, E, B]): EitherT[cats.effect.IO, E, B] = ???
//
//      override def sleep(duration: Duration): EitherTResult.Or[Nothing, Unit] = ???
//
//      override def redeem[E, A, E2, B](r: EitherTResult.Or[E, A])(err: E => EitherT[cats.effect.IO, E2, B], succ: A => EitherT[cats.effect.IO, E2, B]): EitherT[cats.effect.IO, E2, B] = ???
//
//      override def sandboxWith[E, A, E2, B](r: EitherTResult.Or[E, A])(f: EitherT[cats.effect.IO, Either[List[Throwable], E], A] => EitherT[cats.effect.IO, Either[List[Throwable], E2], B]): EitherT[cats.effect.IO, E2, B] = ???
//
//      override def retryOrElse[A, E, A2 >: A, E1 >: E, S, E2](r: EitherTResult.Or[E, A])(duration: FiniteDuration, orElse: => EitherT[cats.effect.IO, E2, A2]): EitherT[cats.effect.IO, E2, A2] = ???
//    }
}





