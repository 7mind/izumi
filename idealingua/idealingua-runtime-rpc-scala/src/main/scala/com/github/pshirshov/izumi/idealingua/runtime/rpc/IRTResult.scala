package com.github.pshirshov.izumi.idealingua.runtime.rpc


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

}





