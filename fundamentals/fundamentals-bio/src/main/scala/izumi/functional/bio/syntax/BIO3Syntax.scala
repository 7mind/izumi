package izumi.functional.bio.syntax

import izumi.functional.bio.{BIOExit, BIOFiber3, BIOPrimitives3}
import izumi.functional.bio.instances._
import izumi.functional.bio.syntax.BIO3Syntax.BIO3ImplicitPuns

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.language.implicitConversions

trait BIO3Syntax extends BIO3ImplicitPuns {
  /**
    * A convenient dependent summoner for BIO* hierarchy.
    * Auto-narrows to the most powerful available class:
    *
    * {{{
    *   def y[F[+_, +_]: BIOTemporal] = {
    *     F.timeout(5.seconds)(F.forever(F.unit))
    *   }
    * }}}
    *
    */
  def FR[FR[-_, +_, +_]](implicit FR: BIOFunctor3[FR]): FR.type = FR
}

object BIO3Syntax {

  class BIOFunctor3Ops[F[-_, _, +_], R, E, A](protected[this] val r: F[R, E, A])(implicit protected[this] val F: BIOFunctor3[F]) {
    @inline final def map[B](f: A => B): F[R, E, B] = F.map(r)(f)

    @inline final def as[B](b: => B): F[R, E, B] = F.map(r)(_ => b)
    @inline final def void: F[R, E, Unit] = F.void(r)
    @inline final def widen[A1](implicit @deprecated("unused", "") ev: A <:< A1): F[R, E, A1] = r.asInstanceOf[F[R, E, A1]]
  }

  class BIOBifunctor3Ops[F[-_, +_, +_], R, E, A](override protected[this] val r: F[R, E, A])(implicit override protected[this] val F: BIOBifunctor3[F])
    extends BIOFunctor3Ops(r) {
    @inline final def leftMap[E2](f: E => E2): F[R, E2, A] = F.leftMap(r)(f)
    @inline final def bimap[E2, B](f: E => E2, g: A => B): F[R, E2, B] = F.bimap(r)(f, g)

    @inline final def widenError[E1](implicit @deprecated("unused", "") ev: E <:< E1): F[R, E1, A] = r.asInstanceOf[F[R, E1, A]]
    @inline final def widenBoth[E1, A1](implicit @deprecated("unused", "") ev: E <:< E1, @deprecated("unused", "") ev2: A <:< A1): F[R, E1, A1] =
      r.asInstanceOf[F[R, E1, A1]]
  }

  class BIOApplicative3Ops[F[-_, +_, +_], R, E, A](override protected[this] val r: F[R, E, A])(implicit override protected[this] val F: BIOApplicative3[F])
    extends BIOBifunctor3Ops(r) {

    /** execute two operations in order, return result of second operation */
    @inline final def *>[E1 >: E, B](f0: => F[R, E1, B]): F[R, E1, B] = F.*>(r, f0)

    /** execute two operations in order, same as `*>`, but return result of first operation */
    @inline final def <*[E1 >: E, B](f0: => F[R, E1, B]): F[R, E1, A] = F.<*(r, f0)

    /** execute two operations in order, return result of both operations */
    @inline final def zip[E2 >: E, B, C](r2: => F[R, E2, B]): F[R, E2, (A, B)] = F.map2(r, r2)(_ -> _)

    /** execute two operations in order, map their results */
    @inline final def map2[E2 >: E, B, C](r2: => F[R, E2, B])(f: (A, B) => C): F[R, E2, C] = F.map2(r, r2)(f)

    @inline final def forever: F[R, E, Nothing] = F.forever(r)
  }

  class BIOGuarantee3Ops[F[-_, +_, +_], R, E, A](override protected[this] val r: F[R, E, A])(implicit override protected[this] val F: BIOGuarantee3[F])
    extends BIOApplicative3Ops(r) {
    @inline final def guarantee(cleanup: F[R, Nothing, Unit]): F[R, E, A] = F.guarantee(r)(cleanup)
  }

  final class BIOMonad3Ops[F[-_, +_, +_], R, E, A](override protected[this] val r: F[R, E, A])(implicit override protected[this] val F: BIOMonad3[F])
    extends BIOApplicative3Ops(r) {
    @inline final def flatMap[E1 >: E, B](f0: A => F[R, E1, B]): F[R, E1, B] = F.flatMap[R, E, A, E1, B](r)(f0)
    @inline final def tap[E1 >: E, B](f0: A => F[R, E1, Unit]): F[R, E1, A] = F.flatMap[R, E, A, E1, A](r)(a => F.map(f0(a))(_ => a))

    @inline final def flatten[E1 >: E, A1](implicit ev: A <:< F[R, E1, A1]): F[R, E1, A1] = F.flatten(F.widen(r))
  }

  class BIOError3Ops[F[-_, +_, +_], R, E, A](override protected[this] val r: F[R, E, A])(implicit override protected[this] val F: BIOError3[F])
    extends BIOGuarantee3Ops(r) {
    @inline final def catchAll[E2, A2 >: A](h: E => F[R, E2, A2]): F[R, E2, A2] = F.catchAll[R, E, A, E2, A2](r)(h)
    @inline final def catchSome[E2 >: E, A2 >: A](h: PartialFunction[E, F[R, E2, A2]]): F[R, E2, A2] = F.catchSome[R, E, A, E2, A2](r)(h)

    @inline final def redeemPure[B](err: E => B, succ: A => B): F[R, Nothing, B] = F.redeemPure(r)(err, succ)

    @inline final def tapError[E1 >: E](f: E => F[R, E1, Unit]): F[R, E1, A] = F.tapError[R, E, A, E1](r)(f)

    @inline final def attempt: F[R, Nothing, Either[E, A]] = F.attempt(r)
  }

  class BIOMonadError3Ops[F[-_, +_, +_], R, E, A](override protected[this] val r: F[R, E, A])(implicit override protected[this] val F: BIOMonadError3[F])
    extends BIOError3Ops(r) {
    @inline final def flatMap[E1 >: E, B](f0: A => F[R, E1, B]): F[R, E1, B] = F.flatMap[R, E, A, E1, B](r)(f0)
    @inline final def tap[E1 >: E, B](f0: A => F[R, E1, Unit]): F[R, E1, A] = F.flatMap[R, E, A, E1, A](r)(a => F.map(f0(a))(_ => a))

    @inline final def flatten[E1 >: E, A1](implicit ev: A <:< F[R, E1, A1]): F[R, E1, A1] = F.flatten(F.widen(r))

    @inline final def redeem[E2, B](err: E => F[R, E2, B], succ: A => F[R, E2, B]): F[R, E2, B] = F.redeem[R, E, A, E2, B](r)(err, succ)

    @inline final def leftFlatMap[E2](f: E => F[R, Nothing, E2]): F[R, E2, A] = F.leftFlatMap(r)(f)
    @inline final def flip: F[R, A, E] = F.flip(r)

    @inline final def tapBoth[E1 >: E, E2 >: E1](err: E => F[R, E1, Unit])(succ: A => F[R, E2, Unit]): F[R, E2, A] = F.tapBoth[R, E, A, E2](r)(err, succ)

    @inline final def fromEither[E1 >: E, A1](implicit ev: A <:< Either[E1, A1]): F[R, E1, A1] = F.flatMap[R, E, A, E1, A1](r)(F.fromEither[E1, A1](_))
    @inline final def fromOption[E1 >: E, A1](errorOnNone: => E1)(implicit ev1: A <:< Option[A1]): F[R, E1, A1] =
      F.flatMap[R, E, A, E1, A1](r)(F.fromOption(errorOnNone)(_))

    /** for-comprehensions sugar:
      *
      * {{{
      *   for {
      *    (1, 2) <- F.pure((2, 1))
      *   } yield ()
      * }}}
      */
    @inline final def withFilter[E1 >: E](predicate: A => Boolean)(implicit ev: NoSuchElementException <:< E1): F[R, E1, A] = F.withFilter[R, E1, A](r)(predicate)
  }

  class BIOBracket3Ops[F[-_, +_, +_], R, E, A](override protected[this] val r: F[R, E, A])(implicit override protected[this] val F: BIOBracket3[F])
    extends BIOMonadError3Ops(r) {
    @inline final def bracket[E1 >: E, B](release: A => F[R, Nothing, Unit])(use: A => F[R, E1, B]): F[R, E1, B] =
      F.bracket(r: F[R, E1, A])(release)(use)
  }

  class BIOPanic3Ops[F[-_, +_, +_], R, E, A](override protected[this] val r: F[R, E, A])(implicit override protected[this] val F: BIOPanic3[F])
    extends BIOBracket3Ops(r) {
    @inline final def sandbox: F[R, BIOExit.Failure[E], A] = F.sandbox(r)
    @inline final def sandboxBIOExit: F[R, Nothing, BIOExit[E, A]] = F.redeemPure(F.sandbox(r))(identity, BIOExit.Success(_))

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
      */
    @inline final def sandboxToThrowable(implicit ev: E <:< Throwable): F[R, Throwable, A] =
      F.leftMap(F.sandbox(r))(_.toThrowable)

    /** Convert Throwable typed error into a defect */
    @inline final def orTerminate(implicit ev: E <:< Throwable): F[R, Nothing, A] = F.catchAll(r)(F.terminate(_))
  }

  class BIO3Ops[F[-_, +_, +_], R, E, A](override protected[this] val r: F[R, E, A])(implicit override protected[this] val F: BIO3[F]) extends BIOPanic3Ops(r) {
    @inline final def bracketAuto[E1 >: E, B](use: A => F[R, E1, B])(implicit ev: A <:< AutoCloseable): F[R, E1, B] =
      F.bracket[R, E1, A, B](r)(c => F.sync(c.close()))(use)
  }

  class BIOAsync3Ops[F[-_, +_, +_], R, E, A](override protected[this] val r: F[R, E, A])(implicit override protected[this] val F: BIOAsync3[F]) extends BIO3Ops(r) {
    @inline final def race[E1 >: E, A1 >: A](that: F[R, E1, A1]): F[R, E1, A1] = F.race(r, that)
  }

  final class BIOTemporal3Ops[F[-_, +_, +_], R, E, A](override protected[this] val r: F[R, E, A])(implicit override protected[this] val F: BIOTemporal3[F])
    extends BIOAsync3Ops(r) {
    @inline final def retryOrElse[A2 >: A, E2](duration: FiniteDuration, orElse: => F[R, E2, A2]): F[R, E2, A2] = F.retryOrElse[R, A, E, A2, E2](r)(duration, orElse)
    @inline final def repeatUntil[E2 >: E, A2](onTimeout: => E2, sleep: FiniteDuration, maxAttempts: Int)(implicit ev: A <:< Option[A2]): F[R, E2, A2] =
      F.repeatUntil[R, E2, A2](new BIOFunctor3Ops(r)(F).widen)(onTimeout, sleep, maxAttempts)

    @inline final def timeout(duration: Duration): F[R, E, Option[A]] = F.timeout(r)(duration)
    @inline final def timeoutFail[E1 >: E](e: E1)(duration: Duration): F[R, E1, A] =
      F.flatMap(timeout(duration): F[R, E1, Option[A]])(_.fold[F[R, E1, A]](F.fail(e))(F.pure))

  }

  final class BIOFork3Ops[F[-_, +_, +_], R, E, A](private val r: F[R, E, A])(implicit private val F: BIOFork3[F]) {
    @inline final def fork: F[R, Nothing, BIOFiber3[F[-?, +?, +?], R, E, A]] = F.fork(r)
  }

  trait BIO3ImplicitPuns extends BIO3ImplicitPuns1 {
    @inline implicit final def BIOTemporal3[F[-_, +_, +_]: BIOTemporal3, R, E, A](self: F[R, E, A]): BIO3Syntax.BIOTemporal3Ops[F, R, E, A] =
      new BIO3Syntax.BIOTemporal3Ops[F, R, E, A](self)
    @inline final def BIOTemporal3[F[-_, +_, +_]: BIOTemporal3]: BIOTemporal3[F] = implicitly

    @inline implicit final def BIOFork3[F[-_, +_, +_]: BIOFork3, R, E, A](self: F[R, E, A]): BIO3Syntax.BIOFork3Ops[F, R, E, A] =
      new BIO3Syntax.BIOFork3Ops[F, R, E, A](self)
    @inline final def BIOFork3[F[-_, +_, +_]: BIOFork3]: BIOFork3[F] = implicitly

    @inline final def BIOPrimitives3[F[-_, +_, +_]: BIOPrimitives3]: BIOPrimitives[F[Any, +?, +?]] = implicitly
  }
  trait BIO3ImplicitPuns1 extends BIO3ImplicitPuns2 {
    @inline implicit final def BIOAsync3[F[-_, +_, +_]: BIOAsync3, R, E, A](self: F[R, E, A]): BIO3Syntax.BIOAsync3Ops[F, R, E, A] =
      new BIO3Syntax.BIOAsync3Ops[F, R, E, A](self)
    @inline final def BIOAsync3[F[-_, +_, +_]: BIOAsync3]: BIOAsync3[F] = implicitly
  }
  trait BIO3ImplicitPuns2 extends BIO3ImplicitPuns3 {
    @inline implicit final def BIO3[F[-_, +_, +_]: BIO3, R, E, A](self: F[R, E, A]): BIO3Syntax.BIO3Ops[F, R, E, A] = new BIO3Syntax.BIO3Ops[F, R, E, A](self)
    /**
      * Shorthand for [[BIO#syncThrowable]]
      *
      * {{{
      *   BIO(println("Hello world!"))
      * }}}
      */
    @inline final def BIO3[F[-_, +_, +_], A](effect: => A)(implicit F: BIO3[F]): F[Any, Throwable, A] = F.syncThrowable(effect)
    @inline final def BIO3[F[-_, +_, +_]: BIO3]: BIO3[F] = implicitly
  }
  trait BIO3ImplicitPuns3 extends BIO3ImplicitPuns4 {
    @inline implicit final def BIOPanic3[F[-_, +_, +_]: BIOPanic3, R, E, A](self: F[R, E, A]): BIO3Syntax.BIOPanic3Ops[F, R, E, A] =
      new BIO3Syntax.BIOPanic3Ops[F, R, E, A](self)
    @inline final def BIOPanic3[F[-_, +_, +_]: BIOPanic3]: BIOPanic3[F] = implicitly
  }
  trait BIO3ImplicitPuns4 extends BIO3ImplicitPuns5 {
    @inline implicit final def BIOBracket3[F[-_, +_, +_]: BIOBracket3, R, E, A](self: F[R, E, A]): BIO3Syntax.BIOBracket3Ops[F, R, E, A] =
      new BIO3Syntax.BIOBracket3Ops[F, R, E, A](self)
    @inline final def BIOBracket3[F[-_, +_, +_]: BIOBracket3]: BIOBracket3[F] = implicitly
  }
  trait BIO3ImplicitPuns5 extends BIO3ImplicitPuns6 {
    @inline implicit final def BIOMonadError3[F[-_, +_, +_]: BIOMonadError3, R, E, A](self: F[R, E, A]): BIO3Syntax.BIOMonadError3Ops[F, R, E, A] =
      new BIO3Syntax.BIOMonadError3Ops[F, R, E, A](self)
    @inline final def BIOMonadError3[F[-_, +_, +_]: BIOMonadError3]: BIOMonadError3[F] = implicitly
  }
  trait BIO3ImplicitPuns6 extends BIO3ImplicitPuns7 {
    @inline implicit final def BIOError3[F[-_, +_, +_]: BIOError3, R, E, A](self: F[R, E, A]): BIO3Syntax.BIOError3Ops[F, R, E, A] =
      new BIO3Syntax.BIOError3Ops[F, R, E, A](self)
    @inline final def BIOError3[F[-_, +_, +_]: BIOError3]: BIOError3[F] = implicitly
  }
  trait BIO3ImplicitPuns7 extends BIO3ImplicitPuns8 {
    @inline implicit final def BIOGuarantee3[F[-_, +_, +_]: BIOGuarantee3, R, E, A](self: F[R, E, A]): BIO3Syntax.BIOGuarantee3Ops[F, R, E, A] =
      new BIO3Syntax.BIOGuarantee3Ops[F, R, E, A](self)
    @inline final def BIOGuarantee3[F[-_, +_, +_]: BIOGuarantee3]: BIOGuarantee3[F] = implicitly
  }
  trait BIO3ImplicitPuns8 extends BIO3ImplicitPuns9 {
    @inline implicit final def BIOMonad3[F[-_, +_, +_]: BIOMonad3, R, E, A](self: F[R, E, A]): BIO3Syntax.BIOMonad3Ops[F, R, E, A] =
      new BIO3Syntax.BIOMonad3Ops[F, R, E, A](self)
    @inline final def BIOMonad3[F[-_, +_, +_]: BIOMonad3]: BIOMonad3[F] = implicitly
  }
  trait BIO3ImplicitPuns9 extends BIO3ImplicitPuns10 {
    @inline implicit final def BIOApplicative3[F[-_, +_, +_]: BIOApplicative3, R, E, A](self: F[R, E, A]): BIO3Syntax.BIOApplicative3Ops[F, R, E, A] =
      new BIO3Syntax.BIOApplicative3Ops[F, R, E, A](self)
    @inline final def BIOApplicative3[F[-_, +_, +_]: BIOApplicative3]: BIOApplicative3[F] = implicitly
  }
  trait BIO3ImplicitPuns10 extends BIO3ImplicitPuns11 {
    @inline implicit final def BIOBifunctor3[F[-_, +_, +_]: BIOBifunctor3, R, E, A](self: F[R, E, A]): BIO3Syntax.BIOBifunctor3Ops[F, R, E, A] =
      new BIO3Syntax.BIOBifunctor3Ops[F, R, E, A](self)
    @inline final def BIOBifunctor3[F[-_, +_, +_]: BIOBifunctor3]: BIOBifunctor3[F] = implicitly
  }
  trait BIO3ImplicitPuns11 {
    @inline implicit final def BIOFunctor3[F[-_, _, +_]: BIOFunctor3, R, E, A](self: F[R, E, A]): BIO3Syntax.BIOFunctor3Ops[F, R, E, A] =
      new BIO3Syntax.BIOFunctor3Ops[F, R, E, A](self)
    @inline final def BIOFunctor3[F[-_, _, +_]: BIOFunctor3]: BIOFunctor3[F] = implicitly
  }

}
