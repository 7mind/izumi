package com.github.pshirshov.izumi.functional.bio

import scala.concurrent.duration.FiniteDuration
import scala.language.implicitConversions

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
