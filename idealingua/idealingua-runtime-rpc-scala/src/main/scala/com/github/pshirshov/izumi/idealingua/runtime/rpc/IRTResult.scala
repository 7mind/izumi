package com.github.pshirshov.izumi.idealingua.runtime.rpc


import cats.arrow.FunctionK
import scalaz.zio.IO

import scala.language.higherKinds

trait IRTResult[R[+_, +_]] {
  type Or[+E, +V] = R[E, V]
  type Just[+V] = R[Nothing, V]

  @inline def fromEither[E, V](v: => Either[E, V]): Or[E, V]

  @inline def point[V](v: => V): Just[V]

  @inline def now[A](a: A): Just[A]

  @inline def fail[E](v: => E): Or[E, Nothing]

  @inline def terminate[V](v: => Throwable): Just[V]

  @inline def maybe[V](v: => Either[Throwable, V]): Just[V] = {
    v match {
      case Left(f) =>
        terminate(f)
      case Right(r) =>
        point(r)
    }
  }

  final val unit: Just[Unit] = now(())

  //

  @inline def map[E, A, B](r: Or[E, A])(f: A => B): R[E, B]

  @inline def leftMap[E, A, E2](r: Or[E, A])(f: E => E2): R[E2, A]

  @inline def bimap[E, A, E2, B](r: Or[E, A])(f: E => E2, g: A => B): R[E2, B]

  @inline def flatMap[E, A, E1 >: E, B](r: Or[E, A])(f0: A => R[E1, B]): R[E1, B]

  @inline def redeem[E, A, E2, B](r: Or[E, A])(err: E => R[E2, B], succ: A => R[E2, B]): R[E2, B]

}

object IRTResult {
  implicit class IRTResultApi[R[+_, +_] : IRTResult, +E, +A](r: R[E, A]) {
    val R: IRTResult[R] = implicitly[IRTResult[R]]

    @inline def map[B](f: A => B): R[E, B] = R.map(r)(f)

    @inline def leftMap[E2](f: E => E2): R[E2, A] = R.leftMap(r)(f)

    @inline def bimap[E2, B](f: E => E2, g: A => B): R[E2, B] = R.bimap(r)(f, g)

    @inline def flatMap[E1 >: E, B](f0: A => R[E1, B]): R[E1, B] = R.flatMap[E, A, E1, B](r)(f0)

    @inline def redeem[E2, B](err: E => R[E2, B], succ: A => R[E2, B]): R[E2, B] = R.redeem[E, A, E2, B](r)(err, succ)

    @inline final def redeemPure[E2, B](err: E => B, succ: A => B): R[E2, B] =
      redeem(err.andThen(R.now), succ.andThen(R.now))

  }
}

trait IRTResultTransZio[R[+_, +_]] extends IRTResult[R] {
  def toZio[E]: FunctionK[R[E, ?], IO[E, ?]]

  def fromZio[E]: FunctionK[IO[E, ?], R[E, ?]]
}

object IRTResultTransZio {
  //type xyz[λ[(-[A], +[B]) => Function2[A, Int, B]]]

  //type xyz[λ[(`E[+_]`, `A[+_]`) => EitherT[cats.effect.IO, E, A]]]
//  @deprecated("ZIO<->EitherT adapter is not recommended to use", "")
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


  implicit object IRTResultZio extends IRTResultTransZio[IO] {
    @inline def now[A](a: A): IRTResultZio.Just[A] = IO.now(a)

    @inline def fromEither[L, R](v: => Either[L, R]): Or[L, R] = IO.fromEither(v)

    @inline def point[R](v: => R): Just[R] = IO.point(v)

    @inline def terminate[R](v: => Throwable): Just[R] = IO.terminate(v)

    @inline def fail[E](v: => E): Or[E, Nothing] = IO.fail(v)

    @inline def map[E, A, B](r: Or[E, A])(f: A => B): Or[E, B] = r.map(f)

    @inline def leftMap[E, A, E2](r: IRTResultZio.Or[E, A])(f: E => E2): IRTResultZio.Or[E2, A] = r.leftMap(f)

    @inline def bimap[E, A, E2, B](r: Or[E, A])(f: E => E2, g: A => B): Or[E2, B] = r.bimap(f, g)

    @inline def flatMap[E, A, E1 >: E, B](r: Or[E, A])(f0: A => IO[E1, B]): Or[E1, B] = r.flatMap(f0)

    @inline def redeem[E, A, E2, B](r: Or[E, A])(err: E => Or[E2, B], succ: A => Or[E2, B]): IO[E2, B] = r.redeem(err, succ)

    @inline def toZio[E]: FunctionK[IO[E, ?], IO[E, ?]] = FunctionK.id

    @inline def fromZio[E]: FunctionK[IO[E, ?], IO[E, ?]] = FunctionK.id
  }

}

