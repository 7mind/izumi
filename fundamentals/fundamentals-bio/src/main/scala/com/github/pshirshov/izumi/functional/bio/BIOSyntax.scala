package com.github.pshirshov.izumi.functional.bio

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.language.implicitConversions

trait BIOSyntax {

  @inline implicit def ToOps[R[+ _, + _] : BIO, E, A](self: R[E, A]): BIOSyntax.BIOOps[R, E, A] = new BIOSyntax.BIOOps[R, E, A](self)

  @inline implicit def ToAsyncOps[R[+ _, + _] : BIOAsync, E, A](self: R[E, A]): BIOSyntax.BIOAsyncOps[R, E, A] = new BIOSyntax.BIOAsyncOps[R, E, A](self)

  @inline implicit def ToFlattenOps[R[+ _, + _] : BIO, E, A](self: R[E, R[E, A]]): BIOSyntax.BIOFlattenOps[R, E, A] = new BIOSyntax.BIOFlattenOps[R, E, A](self)

  @inline implicit def ToForkOps[R[_, _] : BIOFork, E, A](self: R[E, A]): BIOSyntax.BIOForkOps[R, E, A] = new BIOSyntax.BIOForkOps[R, E, A](self)

}

object BIOSyntax {

  final class BIOOps[R[+ _, + _], E, A](private val r: R[E, A])(implicit private val R: BIO[R]) {
    @inline def map[B](f: A => B): R[E, B] = R.map(r)(f)

    @inline def as[B](b: B): R[E, B] = R.map(r)(_ => b)

    @inline def leftMap[E2](f: E => E2): R[E2, A] = R.leftMap(r)(f)

    @inline def leftFlatMap[E2](f: E => R[Nothing, E2]): R[E2, A] = R.leftFlatMap(r)(f)

    @inline def flip: R[A, E] = R.flip(r)

    @inline def bimap[E2, B](f: E => E2, g: A => B): R[E2, B] = R.bimap(r)(f, g)

    @inline def flatMap[E1 >: E, B](f0: A => R[E1, B]): R[E1, B] = R.flatMap[E, A, E1, B](r)(f0)

    @inline def *>[E1 >: E, B](f0: => R[E1, B]): R[E1, B] = R.flatMap[E, A, E1, B](r)(_ => f0)

    @inline def redeem[E2, B](err: E => R[E2, B], succ: A => R[E2, B]): R[E2, B] = R.redeem[E, A, E2, B](r)(err, succ)

    @inline def redeemPure[B](err: E => B, succ: A => B): R[Nothing, B] =
      redeem(err.andThen(R.now), succ.andThen(R.now))

    @inline def sandboxWith[E2, B](f: R[BIOExit.Failure[E], A] => R[BIOExit.Failure[E2], B]): R[E2, B] = R.sandboxWith(r)(f)

    @inline def sandbox: R[BIOExit.Failure[E], A] = R.sandbox(r)

    @inline def bracket[E1 >: E, B](release: A => R[Nothing, Unit])(use: A => R[E1, B]): R[E1, B] =
      R.bracket(r: R[E1, A])(release)(use)

    @inline def bracketAuto[E1 >: E, B](use: A => R[E1, B])(implicit ev: A <:< AutoCloseable): R[E1, B] =
      bracket[E1, B](c => R.sync(c.close()))(use)

    @inline def void: R[E, Unit] = R.void(r)

    @inline def catchAll[E2, A2 >: A](h: E => R[E2, A2]): R[E2, A2] = R.redeem(r)(h, R.now)

    @inline def orTerminate(implicit ev: E <:< Throwable): R[Nothing, A] = catchAll(R.terminate(_))

    @inline def widen[E1 >: E, A1 >: A]: R[E1, A1] = r

    @inline def attempt: R[Nothing, Either[E, A]] = redeemPure(Left(_), Right(_))

    @inline def fromEither[E1 >: E, A1](implicit ev: A <:< Either[E1, A1]): R[E1, A1] = flatMap(R.fromEither(_))

    @inline def fromOption[E1 >: E, A1](errorOnNone: E1)(implicit ev1: A <:< Option[A1]): R[E1, A1] = flatMap(R.fromOption(errorOnNone)(_))

    @inline def fromOption[A1](implicit ev: E =:= Nothing, ev1: A <:< Option[A1]): R[Unit, A1] = R.flatMap(redeemPure(ev, identity))(R.fromOption(_))
  }

  final class BIOAsyncOps[R[+ _, + _], E, A](private val r: R[E, A])(implicit private val R: BIOAsync[R]) {
    @inline def retryOrElse[A2 >: A, E2](duration: FiniteDuration, orElse: => R[E2, A2]): R[E2, A2] = R.retryOrElse[A, E, A2, E2](r)(duration, orElse)

    @inline def timeout(duration: Duration): R[E, Option[A]] = R.timeout(r)(duration)

    @inline def timeoutFail[E1 >: E](e: E1)(duration: Duration): R[E1, A] =
      R.flatMap(timeout(duration): R[E1, Option[A]])(_.fold[R[E1, A]](R.fail(e))(R.now))

    @inline def race[E1 >: E, A1 >: A](that: R[E1, A1]): R[E1, A1] = R.race[E1, A1](r)(that)
  }

  final class BIOFlattenOps[R[+ _, + _], E, A](private val r: R[E, R[E, A]])(implicit private val R: BIO[R]) {
    @inline def flatten: R[E, A] = R.flatMap(r)(a => a)
  }

  final class BIOForkOps[R[_, _], E, A](private val r: R[E, A])(implicit private val R: BIOFork[R]) {
    @inline def fork: R[Nothing, BIOFiber[R, E, A]] = R.fork(r)
  }

}
