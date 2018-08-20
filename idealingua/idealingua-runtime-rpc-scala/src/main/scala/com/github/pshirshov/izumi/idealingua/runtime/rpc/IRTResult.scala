package com.github.pshirshov.izumi.idealingua.runtime.rpc


import scalaz.zio.{ExitResult, IO, Retry}

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.language.higherKinds

trait IRTResult[R[+ _, + _]] {
  type Or[+E, +V] = R[E, V]
  type Just[+V] = R[Nothing, V]

  @inline def fromEither[E, V](v: => Either[E, V]): Or[E, V]

  @inline def point[V](v: => V): Just[V]

  @inline def now[A](a: A): Just[A]

  @inline def fail[E](v: => E): Or[E, Nothing]

  @inline def terminate[V](v: => Throwable): Just[V]

  @inline def syncThrowable[A](effect: => A): Or[Throwable, A]

  @inline def sync[A](effect: => A): Or[Nothing, A]

  @inline def maybe[V](v: => Either[Throwable, V]): Just[V] = {
    v match {
      case Left(f) =>
        terminate(f)
      case Right(r) =>
        point(r)
    }
  }

  final val unit: Just[Unit] = now(())

  @inline def bracket0[E, A, B](acquire: R[E, A])(release: A => R[Nothing, Unit])(use: A => R[E, B]): R[E, B]

  //
  @inline def sleep(duration: Duration): Or[Nothing, Unit]

  @inline def map[E, A, B](r: Or[E, A])(f: A => B): R[E, B]

  @inline def leftMap[E, A, E2](r: Or[E, A])(f: E => E2): R[E2, A]

  @inline def bimap[E, A, E2, B](r: Or[E, A])(f: E => E2, g: A => B): R[E2, B]

  @inline def flatMap[E, A, E1 >: E, B](r: Or[E, A])(f0: A => R[E1, B]): R[E1, B]

  @inline def redeem[E, A, E2, B](r: Or[E, A])(err: E => R[E2, B], succ: A => R[E2, B]): R[E2, B]

  @inline def sandboxWith[E, A, E2, B](r: Or[E, A])(f: R[Either[List[Throwable], E], A] => R[Either[List[Throwable], E2], B]): R[E2, B]

  @inline def retryOrElse[A, E, A2 >: A, E1 >: E, S, E2](r: Or[E, A])(duration: FiniteDuration, orElse: => R[E2, A2]): R[E2, A2]
}

trait IRTResultApi[T[K[+ _, + _]] <: IRTResult[K]] {

  implicit class IRTResultApi[R[+ _, + _] : T, +E, +A](val r: R[E, A]) {
    val R: T[R] = implicitly

    @inline def map[B](f: A => B): R[E, B] = R.map(r)(f)

    @inline def leftMap[E2](f: E => E2): R[E2, A] = R.leftMap(r)(f)

    @inline def bimap[E2, B](f: E => E2, g: A => B): R[E2, B] = R.bimap(r)(f, g)

    @inline def flatMap[E1 >: E, B](f0: A => R[E1, B]): R[E1, B] = R.flatMap[E, A, E1, B](r)(f0)

    @inline def redeem[E2, B](err: E => R[E2, B], succ: A => R[E2, B]): R[E2, B] = R.redeem[E, A, E2, B](r)(err, succ)

    @inline final def redeemPure[E2, B](err: E => B, succ: A => B): R[E2, B] =
      redeem(err.andThen(R.now), succ.andThen(R.now))

    @inline def sandboxWith[E2, B](f: R[Either[List[Throwable], E], A] => R[Either[List[Throwable], E2], B]): R[E2, B] = R.sandboxWith(r)(f)

    @inline def retryOrElse[A2 >: A, E1 >: E, S, E2](duration: FiniteDuration, orElse: => R[E2, A2]): R[E2, A2] = R.retryOrElse[A, E, A2, E1, S, E2](r)(duration, orElse)
  }

  def apply[R[+ _, + _] : T, E, A](r: R[E, A]): IRTResultApi[R, E, A] = new IRTResultApi[R, E, A](r)
}

object IRTResult extends IRTResultApi[IRTResult] {

  implicit object IRTResultZio extends IRTResult[IO] {
    @inline def bracket0[E, A, B](acquire: Or[E, A])(release: A => Or[Nothing, Unit])(use: A => Or[E, B]): Or[E, B] =
      IO.bracket0(acquire)((v, _: ExitResult[E, B]) => release(v))(use)

    @inline def sleep(duration: Duration): Or[Nothing, Unit] = IO.sleep(duration)

    @inline def sync[A](effect: => A): Or[Nothing, A] = IO.sync(effect)

    @inline def now[A](a: A): Just[A] = IO.now(a)

    @inline def syncThrowable[A](effect: => A): Or[Throwable, A] = IO.syncThrowable(effect)

    @inline def fromEither[L, R](v: => Either[L, R]): Or[L, R] = IO.fromEither(v)

    @inline def point[R](v: => R): Just[R] = IO.point(v)

    @inline def terminate[R](v: => Throwable): Just[R] = IO.terminate(v)

    @inline def fail[E](v: => E): Or[E, Nothing] = IO.fail(v)

    @inline def map[E, A, B](r: Or[E, A])(f: A => B): Or[E, B] = r.map(f)

    @inline def leftMap[E, A, E2](r: Or[E, A])(f: E => E2): Or[E2, A] = r.leftMap(f)

    @inline def bimap[E, A, E2, B](r: Or[E, A])(f: E => E2, g: A => B): Or[E2, B] = r.bimap(f, g)

    @inline def flatMap[E, A, E1 >: E, B](r: Or[E, A])(f0: A => IO[E1, B]): Or[E1, B] = r.flatMap(f0)

    @inline def redeem[E, A, E2, B](r: Or[E, A])(err: E => Or[E2, B], succ: A => Or[E2, B]): Or[E2, B] = r.redeem(err, succ)

    @inline def sandboxWith[E, A, E2, B](r: Or[E, A])(f: Or[Either[List[Throwable], E], A] => Or[Either[List[Throwable], E2], B]): Or[E2, B] =
      r.sandboxWith(f)

    @inline def retryOrElse[A, E, A2 >: A, E1 >: E, S, E2](r: Or[E, A])(duration: FiniteDuration, orElse: => Or[E2, A2]): Or[E2, A2] =
      r.retryOrElse(Retry.duration(duration), {
        (_: Any, _: Any) =>
          orElse
      })
  }

  //  @deprecated("BIO<->EitherT adapter is not recommended to use", "")
  //  implicit object EitherTResult extends IRTResult[λ[(`+E`, `+A`) => EitherT[cats.effect.IO, E, A]]] {
  //    def ME[E]: MonadError[Or[E, ?], E] = implicitly
  //
  //    // this isn't nice
  //    @inline def cancel[V](v: => Throwable): Just[V] = ME[Nothing].point(throw v)
  //
  //    @inline def choice[E, V](v: => Either[E, V]): Or[E, V] = v match {
  //      case Right(r) =>
  //        ME[E].pure(r)
  //
  //      case Left(l) =>
  //        ME[E].raiseError(l)
  //    }
  //
  //    @inline def just[V](v: => V): Just[V] = ME[Nothing].pure(v)
  //
  //    @inline def stop[E](v: => E): EitherTResult.Or[E, Nothing] = ME[E].raiseError(v)
  //
  //    @inline def map[E, A, B](r: EitherTResult.Or[E, A])(f: A => B): EitherTResult.Or[E, B] = r.map(f)
  //
  //    @inline def leftMap[E, A, E2](r: EitherTResult.Or[E, A])(f: E => E2): EitherTResult.Or[E2, A] = r.leftMap(f)
  //
  //    @inline def bimap[E, A, E2, B](r: EitherTResult.Or[E, A])(f: E => E2, g: A => B): EitherTResult.Or[E2, B] = r.bimap(f, g)
  //
  //    @inline def flatMap[E, A, E1 >: E, B](r: EitherTResult.Or[E, A])(f0: A => EitherT[effect.IO, E1, B]): EitherT[effect.IO, E1, B] = r.flatMap(f0)
  //  }
}





