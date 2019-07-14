package com.github.pshirshov.izumi.functional.bio

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.language.implicitConversions

trait BIOSyntax {

  /**
   * A convenient dependent summoner for BIO* hierarchy.
   * Auto-narrows to the most powerful available class:
   *
   * {{{
   *   def y[F[+_, +_]: BIOAsync] = {
   *     F.timeout(5.seconds)(F.forever(F.unit))
   *   }
   * }}}
   *
   * */
  @inline final def F[F[+_, +_]](implicit F: BIOFunctor[F]): F.type = F

  /**
   * Automatic converters from BIO* hierarchy to equivalent cats & cats-effect classes.
   */
  final object catz extends BIOCatsConversions

  @inline implicit final def ToFunctorOps[F[_, + _] : BIOFunctor, E, A](self: F[E, A]): BIOSyntax.BIOFunctorOps[F, E, A] = new BIOSyntax.BIOFunctorOps[F, E, A](self)
  @inline implicit final def ToBifunctorOps[F[+ _, + _] : BIOBifunctor, E, A](self: F[E, A]): BIOSyntax.BIOBifunctorOps[F, E, A] = new BIOSyntax.BIOBifunctorOps[F, E, A](self)
  @inline implicit final def ToApplicativeOps[F[+ _, + _] : BIOApplicative, E, A](self: F[E, A]): BIOSyntax.BIOApplicativeOps[F, E, A] = new BIOSyntax.BIOApplicativeOps[F, E, A](self)
  @inline implicit final def ToGuaranteeOps[F[+ _, + _] : BIOGuarantee, E, A](self: F[E, A]): BIOSyntax.BIOGuaranteeOps[F, E, A] = new BIOSyntax.BIOGuaranteeOps[F, E, A](self)
  @inline implicit final def ToMonadErrorOps[F[+ _, + _] : BIOMonadError, E, A](self: F[E, A]): BIOSyntax.BIOMonadErrorOps[F, E, A] = new BIOSyntax.BIOMonadErrorOps[F, E, A](self)
  @inline implicit final def ToMonadOps[F[+ _, + _] : BIOMonad, E, A](self: F[E, A]): BIOSyntax.BIOMonadOps[F, E, A] = new BIOSyntax.BIOMonadOps[F, E, A](self)
  @inline implicit final def ToErrorOps[F[+ _, + _] : BIOError, E, A](self: F[E, A]): BIOSyntax.BIOErrorOps[F, E, A] = new BIOSyntax.BIOErrorOps[F, E, A](self)
  @inline implicit final def ToBracketOps[F[+ _, + _] : BIOBracket, E, A](self: F[E, A]): BIOSyntax.BIOBracketOps[F, E, A] = new BIOSyntax.BIOBracketOps[F, E, A](self)
  @inline implicit final def ToPanicOps[F[+ _, + _] : BIOPanic, E, A](self: F[E, A]): BIOSyntax.BIOPanicOps[F, E, A] = new BIOSyntax.BIOPanicOps[F, E, A](self)

  @inline implicit final def ToOps[F[+ _, + _] : BIO, E, A](self: F[E, A]): BIOSyntax.BIOOps[F, E, A] = new BIOSyntax.BIOOps[F, E, A](self)

  @inline implicit final def ToAsyncOps[R[+ _, + _] : BIOAsync, E, A](self: R[E, A]): BIOSyntax.BIOAsyncOps[R, E, A] = new BIOSyntax.BIOAsyncOps[R, E, A](self)

  @inline implicit final def ToFlattenOps[R[+ _, + _] : BIOMonad, E, A](self: R[E, R[E, A]]): BIOSyntax.BIOFlattenOps[R, E, A] = new BIOSyntax.BIOFlattenOps[R, E, A](self)

  @inline implicit final def ToForkOps[R[_, _] : BIOFork, E, A](self: R[E, A]): BIOSyntax.BIOForkOps[R, E, A] = new BIOSyntax.BIOForkOps[R, E, A](self)

}

object BIOSyntax {

  final class BIOFunctorOps[F[_, + _], E, A](private val r: F[E, A])(implicit private val F: BIOFunctor[F]) {
    @inline def map[B](f: A => B): F[E, B] = F.map(r)(f)

    @inline def as[B](b: B): F[E, B] = F.map(r)(_ => b)

    @inline def widen[A1](implicit ev: A <:< A1): F[E, A1] = { val _ = ev; r.asInstanceOf[F[E, A1]] }

    @inline def void: F[E, Unit] = F.void(r)
  }

  final class BIOBifunctorOps[F[+ _, + _], E, A](private val r: F[E, A])(implicit private val F: BIOBifunctor[F]) {
    @inline def leftMap[E2](f: E => E2): F[E2, A] = F.leftMap(r)(f)

    @inline def bimap[E2, B](f: E => E2, g: A => B): F[E2, B] = F.bimap(r)(f, g)

    @inline def widenError[E1](implicit ev: E <:< E1): F[E1, A] = { val _ = ev; r.asInstanceOf[F[E1, A]] }
  }

  final class BIOApplicativeOps[F[+ _, + _], E, A](private val r: F[E, A])(implicit private val F: BIOApplicative[F]) {
    /** execute two operations in order, map their results */
    @inline def map2[E2 >: E, B, C](r2: => F[E2, B])(f: (A, B) => C): F[E2, C] = F.map2(r, r2)(f)

    /** execute two operations in order, return result of second operation */
    @inline def *>[E1 >: E, B](f0: => F[E1, B]): F[E1, B] = F.*>[E, A, E1, B](r, f0)

    /** execute two operations in order, same as `*>`, but return result of first operation */
    @inline def <*[E1 >: E, B](f0: => F[E1, B]): F[E1, A] = F.<*[E, A, E1, B](r, f0)

    /** execute two operations in order, return result of both operations */
    @inline def zip[E2 >: E, B, C](r2: => F[E2, B]): F[E2, (A, B)] = F.map2(r, r2)(_ -> _)

    @inline def forever: F[E, Nothing] = F.forever(r)
  }

  final class BIOGuaranteeOps[F[+ _, + _], E, A](private val r: F[E, A])(implicit private val F: BIOGuarantee[F]) {
    @inline def guarantee(cleanup: F[Nothing, Unit]): F[E, A] = F.guarantee(r)(cleanup)
  }

  final class BIOMonadOps[F[+ _, + _], E, A](private val r: F[E, A])(implicit private val F: BIOMonad[F]) {
    @inline def flatMap[E1 >: E, B](f0: A => F[E1, B]): F[E1, B] = F.flatMap[E, A, E1, B](r)(f0)

    @inline def tap[E1 >: E, B](f0: A => F[E1, Unit]): F[E1, A] = F.flatMap[E, A, E1, A](r)(a => F.map(f0(a))(_ => a))
  }

  final class BIOErrorOps[F[+ _, + _], E, A](private val r: F[E, A])(implicit private val F: BIOError[F]) {
    @inline def flip: F[A, E] = F.flip(r)

    @inline def redeem[E2, B](err: E => F[E2, B], succ: A => F[E2, B]): F[E2, B] = F.redeem[E, A, E2, B](r)(err, succ)

    @inline def redeemPure[B](err: E => B, succ: A => B): F[Nothing, B] = F.redeemPure(r)(err, succ)

    @inline def catchAll[E2, A2 >: A](h: E => F[E2, A2]): F[E2, A2] = F.catchAll[E, A, E2, A2](r)(h)

    @inline def catchSome[E2 >: E, A2 >: A](h: PartialFunction[E, F[E2, A2]]): F[E2, A2] = F.catchSome[E, A, E2, A2](r)(h)

    @inline def tapError[E1 >: E](f: E => F[E1, Unit]): F[E1, A] = F.catchAll(r)(e => F.*>(f(e), F.fail(e)))

    @inline def attempt: F[Nothing, Either[E, A]] = F.attempt(r)
  }

  final class BIOMonadErrorOps[F[+ _, + _], E, A](private val r: F[E, A])(implicit private val F: BIOBracket[F]) {
    @inline def leftFlatMap[E2](f: E => F[Nothing, E2]): F[E2, A] = F.leftFlatMap(r)(f)

    @inline def bracket[E1 >: E, B](release: A => F[Nothing, Unit])(use: A => F[E1, B]): F[E1, B] =
      F.bracket(r: F[E1, A])(release)(use)

    @inline def tapBoth[E1 >: E, E2 >: E1](err: E => F[E1, Unit])(succ: A => F[E2, Unit]): F[E2, A] = {
      new BIOMonadOps(new BIOErrorOps(r).tapError(err)).tap(succ)
    }

    @inline def fromEither[E1 >: E, A1](implicit ev: A <:< Either[E1, A1]): F[E1, A1] = F.flatMap[E, A, E1, A1](r)(F.fromEither[E1, A1](_))

    @inline def fromOption[E1 >: E, A1](errorOnNone: E1)(implicit ev1: A <:< Option[A1]): F[E1, A1] = F.flatMap[E, A, E1, A1](r)(F.fromOption(errorOnNone)(_))

    @inline def fromOption[A1](implicit ev: E =:= Nothing, ev1: A <:< Option[A1]): F[Unit, A1] = F.flatMap(F.redeemPure(r)(ev, identity))(F.fromOption(_))
  }

  final class BIOBracketOps[F[+ _, + _], E, A](private val r: F[E, A])(implicit private val F: BIOBracket[F]) {
    @inline def bracket[E1 >: E, B](release: A => F[Nothing, Unit])(use: A => F[E1, B]): F[E1, B] =
      F.bracket(r: F[E1, A])(release)(use)
  }

  final class BIOPanicOps[F[+ _, + _], E, A](private val r: F[E, A])(implicit private val F: BIOPanic[F]) {
    @inline def sandbox: F[BIOExit.Failure[E], A] = F.sandbox(r)

    @inline def orTerminate(implicit ev: E <:< Throwable): F[Nothing, A] = F.catchAll(r)(F.terminate(_))

    /**
     * Catch all _defects_ in this effect and convert them to Throwable
     * Example:
     *
     * {{{
     *   BIO[F].pure(1)
     *     .map(_ => ???)
     *     .sandboxThrowable
     *     .catchAll(_ => BIO(println("Caught error!")))
     * }}}
     *
     * */
    @inline def sandboxToThrowable(implicit ev: E <:< Throwable): F[Throwable, A] =
      F.catchAll(F.sandbox(r))(failure => F.fail(failure.toThrowable))
  }

  final class BIOOps[F[+ _, + _], E, A](private val r: F[E, A])(implicit private val F: BIO[F]) {

    @inline def bracketAuto[E1 >: E, B](use: A => F[E1, B])(implicit ev: A <:< AutoCloseable): F[E1, B] =
      F.bracket[E1, A, B](r)(c => F.sync(c.close()))(use)

  }

  final class BIOAsyncOps[F[+ _, + _], E, A](private val r: F[E, A])(implicit private val F: BIOAsync[F]) {
    @inline def retryOrElse[A2 >: A, E2](duration: FiniteDuration, orElse: => F[E2, A2]): F[E2, A2] = F.retryOrElse[A, E, A2, E2](r)(duration, orElse)

    @inline def timeout(duration: Duration): F[E, Option[A]] = F.timeout(r)(duration)

    @inline def timeoutFail[E1 >: E](e: E1)(duration: Duration): F[E1, A] =
      F.flatMap(timeout(duration): F[E1, Option[A]])(_.fold[F[E1, A]](F.fail(e))(F.pure))

    @inline def race[E1 >: E, A1 >: A](that: F[E1, A1]): F[E1, A1] = F.race[E1, A1](r)(that)
  }

  final class BIOFlattenOps[F[+ _, + _], E, A](private val r: F[E, F[E, A]])(implicit private val F: BIOMonad[F]) {
    @inline def flatten: F[E, A] = F.flatten(r)
  }

  final class BIOForkOps[F[_, _], E, A](private val r: F[E, A])(implicit private val F: BIOFork[F]) {
    @inline def fork: F[Nothing, BIOFiber[F, E, A]] = F.fork(r)
  }

}
