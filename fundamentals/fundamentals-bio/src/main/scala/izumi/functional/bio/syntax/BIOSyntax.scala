package izumi.functional.bio.syntax

import izumi.functional.bio._
import izumi.fundamentals.platform.language.{SourceFilePositionMaterializer, unused}

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.language.implicitConversions

object BIOSyntax {

  class BIOFunctorOps[+F[+_, +_], +E, +A](protected[this] val r: F[E, A])(implicit protected[this] val F: Functor2[F]) {
    @inline final def map[B](f: A => B): F[E, B] = F.map(r)(f)

    @inline final def as[B](b: => B): F[E, B] = F.map(r)(_ => b)
    @inline final def void: F[E, Unit] = F.void(r)
    @inline final def widen[A1](implicit @unused ev: A <:< A1): F[E, A1] = r.asInstanceOf[F[E, A1]]
  }

  final class BIOBifunctorOps[+F[+_, +_], +E, +A](protected[this] val r: F[E, A])(implicit protected[this] val F: Bifunctor2[F]) {
    @inline final def leftMap[E2](f: E => E2): F[E2, A] = F.leftMap(r)(f)
    @inline final def bimap[E2, B](f: E => E2, g: A => B): F[E2, B] = F.bimap(r)(f, g)

    @inline final def widenError[E1 >: E]: F[E1, A] = r
    @inline final def widenBoth[E1 >: E, A1](implicit @unused ev2: A <:< A1): F[E1, A1] = r.asInstanceOf[F[E1, A1]]
  }

  class BIOApplicativeOps[F[+_, +_], +E, +A](override protected[this] val r: F[E, A])(implicit override protected[this] val F: Applicative2[F]) extends BIOFunctorOps(r) {

    /** execute two operations in order, return result of second operation */
    @inline final def *>[E1 >: E, B](f0: => F[E1, B]): F[E1, B] = F.*>(r, f0)

    /** execute two operations in order, same as `*>`, but return result of first operation */
    @inline final def <*[E1 >: E, B](f0: => F[E1, B]): F[E1, A] = F.<*(r, f0)

    /** execute two operations in order, return result of both operations */
    @inline final def zip[E2 >: E, B, C](r2: => F[E2, B]): F[E2, (A, B)] = F.map2(r, r2)(_ -> _)

    /** execute two operations in order, map their results */
    @inline final def map2[E2 >: E, B, C](r2: => F[E2, B])(f: (A, B) => C): F[E2, C] = F.map2(r, r2)(f)

    @inline final def forever: F[E, Nothing] = F.forever(r)
  }

  class BIOGuaranteeOps[F[+_, +_], +E, +A](override protected[this] val r: F[E, A])(implicit override protected[this] val F: Guarantee2[F]) extends BIOApplicativeOps(r) {
    @inline final def guarantee(cleanup: F[Nothing, Unit]): F[E, A] = F.guarantee(r, cleanup)
  }

  class BIOApplicativeErrorOps[F[+_, +_], +E, +A](override protected[this] val r: F[E, A])(implicit override protected[this] val F: ApplicativeError2[F])
    extends BIOGuaranteeOps(r) {
    @inline final def leftMap[E2](f: E => E2): F[E2, A] = F.leftMap(r)(f)
    @inline final def bimap[E2, B](f: E => E2, g: A => B): F[E2, B] = F.bimap(r)(f, g)

    @inline final def orElse[E2, A1 >: A](r2: => F[E2, A1]): F[E2, A1] = F.orElse(r, r2)
    @inline final def leftMap2[E2, A1 >: A, E3](r2: => F[E2, A1])(f: (E, E2) => E3): F[E3, A1] = F.leftMap2(r, r2)(f)

    @inline final def widenError[E1 >: E]: F[E1, A] = r
    @inline final def widenBoth[E1 >: E, A1](implicit @unused ev2: A <:< A1): F[E1, A1] = r.asInstanceOf[F[E1, A1]]
  }

  final class BIOMonadOps[F[+_, +_], +E, +A](override protected[this] val r: F[E, A])(implicit override protected[this] val F: Monad2[F]) extends BIOApplicativeOps(r) {
    @inline final def flatMap[E1 >: E, B](f0: A => F[E1, B]): F[E1, B] = F.flatMap[Any, E1, A, B](r)(f0)
    @inline final def tap[E1 >: E, B](f0: A => F[E1, Unit]): F[E1, A] = F.flatMap[Any, E1, A, A](r)(a => F.map(f0(a))(_ => a))

    @inline final def flatten[E1 >: E, A1](implicit ev: A <:< F[E1, A1]): F[E1, A1] = F.flatten(r.widen)
  }

  class BIOErrorOps[F[+_, +_], +E, +A](override protected[this] val r: F[E, A])(implicit override protected[this] val F: Error2[F]) extends BIOApplicativeErrorOps(r) {
    @inline final def flatMap[E1 >: E, B](f0: A => F[E1, B]): F[E1, B] = F.flatMap[Any, E1, A, B](r)(f0)
    @inline final def tap[E1 >: E, B](f0: A => F[E1, Unit]): F[E1, A] = F.flatMap[Any, E1, A, A](r)(a => F.map(f0(a))(_ => a))

    @inline final def flatten[E1 >: E, A1](implicit ev: A <:< F[E1, A1]): F[E1, A1] = F.flatten(r.widen)

    @inline final def catchAll[E2, A2 >: A](h: E => F[E2, A2]): F[E2, A2] = F.catchAll[Any, E, A2, E2](r)(h)
    @inline final def catchSome[E1 >: E, A2 >: A](h: PartialFunction[E, F[E1, A2]]): F[E1, A2] = F.catchSome[Any, E, A2, E1](r)(h)

    @inline final def redeem[E2, B](err: E => F[E2, B], succ: A => F[E2, B]): F[E2, B] = F.redeem[Any, E, A, E2, B](r)(err, succ)
    @inline final def redeemPure[B](err: E => B, succ: A => B): F[Nothing, B] = F.redeemPure(r)(err, succ)

    @inline final def attempt: F[Nothing, Either[E, A]] = F.attempt(r)

    @inline final def tapError[E1 >: E](f: E => F[E1, Unit]): F[E1, A] = F.tapError[Any, E, A, E1](r)(f)

    @inline final def leftFlatMap[E2](f: E => F[Nothing, E2]): F[E2, A] = F.leftFlatMap(r)(f)
    @inline final def flip: F[A, E] = F.flip(r)

    @inline final def tapBoth[E1 >: E, E2 >: E1](err: E => F[E1, Unit])(succ: A => F[E2, Unit]): F[E2, A] = F.tapBoth[Any, E, A, E2](r)(err, succ)

    @inline final def fromEither[E1 >: E, A1](implicit ev: A <:< Either[E1, A1]): F[E1, A1] = F.flatMap[Any, E1, A, A1](r)(F.fromEither[E1, A1](_))
    @inline final def fromOption[E1 >: E, A1](errorOnNone: => E1)(implicit ev1: A <:< Option[A1]): F[E1, A1] = F.flatMap[Any, E1, A, A1](r)(F.fromOption(errorOnNone)(_))

    /** for-comprehensions sugar:
      *
      * {{{
      *   for {
      *     (1, 2) <- F.pure((2, 1))
      *   } yield ()
      * }}}
      *
      * Use [[widenError]] to for pattern matching with non-Throwable errors:
      *
      * {{{
      *   val f = for {
      *     (1, 2) <- F.pure((2, 1)).widenError[Option[Unit]]
      *   } yield ()
      *   // f: F[Option[Unit], Unit] = F.fail(Some(())
      * }}}
      */
    @inline final def withFilter[E1 >: E](predicate: A => Boolean)(implicit filter: WithFilter[E1], pos: SourceFilePositionMaterializer): F[E1, A] =
      F.withFilter[Any, E1, A](r)(predicate)
  }

  class BIOBracketOps[F[+_, +_], +E, +A](override protected[this] val r: F[E, A])(implicit override protected[this] val F: Bracket2[F]) extends BIOErrorOps(r) {
    @inline final def bracket[E1 >: E, B](release: A => F[Nothing, Unit])(use: A => F[E1, B]): F[E1, B] = F.bracket(r: F[E1, A])(release)(use)
    @inline final def bracketCase[E1 >: E, B](release: (A, Exit[E1, B]) => F[Nothing, Unit])(use: A => F[E1, B]): F[E1, B] = F.bracketCase(r: F[E1, A])(release)(use)
    @inline final def guaranteeCase(cleanup: Exit[E, A] => F[Nothing, Unit]): F[E, A] = F.guaranteeCase(r, cleanup)
  }

  class BIOPanicOps[F[+_, +_], +E, +A](override protected[this] val r: F[E, A])(implicit override protected[this] val F: Panic2[F]) extends BIOBracketOps(r) {
    @inline final def sandbox: F[Exit.Failure[E], A] = F.sandbox(r)
    @inline final def sandboxExit: F[Nothing, Exit[E, A]] = F.redeemPure(F.sandbox(r))(identity, Exit.Success(_))
    @deprecated("renamed to sandboxExit", "1.0")
    @inline final def sandboxBIOExit = sandboxExit

    /**
      * Catch all _defects_ in this effect and convert them to Throwable
      * Example:
      *
      * {{{
      *   F.pure(1)
      *     .map(_ => ???)
      *     .sandboxThrowable
      *     .catchAll(_ => IO2(println("Caught error!")))
      * }}}
      */
    @inline final def sandboxToThrowable(implicit ev: E <:< Throwable): F[Throwable, A] =
      F.leftMap(F.sandbox(r))(_.toThrowable)

    /** Convert Throwable typed error into a defect */
    @inline final def orTerminate(implicit ev: E <:< Throwable): F[Nothing, A] = F.catchAll(r)(F.terminate(_))
  }

  class BIOOps[F[+_, +_], +E, +A](override protected[this] val r: F[E, A])(implicit override protected[this] val F: IO2[F]) extends BIOPanicOps(r) {
    @inline final def bracketAuto[E1 >: E, B](use: A => F[E1, B])(implicit ev: A <:< AutoCloseable): F[E1, B] =
      F.bracket[Any, E1, A, B](r)(c => F.sync(c.close()))(use)
  }

  class BIOParallelOps[F[+_, +_], +E, +A](protected[this] val r: F[E, A])(implicit protected[this] val F: Parallel2[F]) {
    @inline final def zipWithPar[E1 >: E, B, C](that: F[E1, B])(f: (A, B) => C): F[E1, C] = F.zipWithPar(r, that)(f)
    @inline final def zipPar[E1 >: E, B](that: F[E1, B]): F[E1, (A, B)] = F.zipPar(r, that)
    @inline final def zipParLeft[E1 >: E, B](that: F[E1, B]): F[E1, A] = F.zipParLeft(r, that)
    @inline final def zipParRight[E1 >: E, B](that: F[E1, B]): F[E1, B] = F.zipParRight(r, that)
  }
  final class BIOConcurrentOps[F[+_, +_], +E, +A](override protected[this] val r: F[E, A])(implicit override protected[this] val F: Concurrent2[F])
    extends BIOParallelOps(r)(F) {
    @inline final def race[E1 >: E, A1 >: A](that: F[E1, A1]): F[E1, A1] = F.race(r, that)
    @inline final def racePair[E1 >: E, A1 >: A](
      that: F[E1, A1]
    ): F[E1, Either[(A, Fiber2[F, E1, A1]), (Fiber2[F, E1, A], A1)]] = F.racePair(r, that)
    @inline final def uninterruptible: F[E, A] = F.uninterruptible(r)
  }
  class BIOAsyncOps[F[+_, +_], +E, +A](override protected[this] val r: F[E, A])(implicit override protected[this] val F: Async2[F]) extends BIOOps(r) {
    @inline final def zipWithPar[E1 >: E, B, C](that: F[E1, B])(f: (A, B) => C): F[E1, C] = F.zipWithPar(r, that)(f)
    @inline final def zipPar[E1 >: E, B](that: F[E1, B]): F[E1, (A, B)] = F.zipPar(r, that)
    @inline final def zipParLeft[E1 >: E, B](that: F[E1, B]): F[E1, A] = F.zipParLeft(r, that)
    @inline final def zipParRight[E1 >: E, B](that: F[E1, B]): F[E1, B] = F.zipParRight(r, that)

    @inline final def race[E1 >: E, A1 >: A](that: F[E1, A1]): F[E1, A1] = F.race(r, that)
    @inline final def racePair[E1 >: E, A1 >: A](
      that: F[E1, A1]
    ): F[E1, Either[(A, Fiber2[F, E1, A1]), (Fiber2[F, E1, A], A1)]] = F.racePair(r, that)
    @inline final def uninterruptible: F[E, A] = F.uninterruptible(r)
  }

  final class BIOTemporalOps[F[+_, +_], +E, +A](protected[this] val r: F[E, A])(implicit protected[this] val F: Temporal2[F]) {
    @inline final def retryOrElse[A2 >: A, E2](duration: FiniteDuration, orElse: => F[E2, A2]): F[E2, A2] = F.retryOrElse[Any, E, A2, E2](r)(duration, orElse)
    @inline final def repeatUntil[E2 >: E, A2](tooManyAttemptsError: => E2, sleep: FiniteDuration, maxAttempts: Int)(implicit ev: A <:< Option[A2]): F[E2, A2] =
      F.repeatUntil[Any, E2, A2](new BIOFunctorOps(r)(F.InnerF).widen)(tooManyAttemptsError, sleep, maxAttempts)

    @inline final def timeout(duration: Duration): F[E, Option[A]] = F.timeout(duration)(r)
    @inline final def timeoutFail[E1 >: E](e: E1)(duration: Duration): F[E1, A] = F.timeoutFail(duration)(e, r)
  }

  final class BIOForkOps[F[+_, +_], +E, +A](private val r: F[E, A])(implicit private val F: Fork2[F]) {
    @inline final def fork: F[Nothing, Fiber2[F, E, A]] = F.fork(r)
  }

  trait BIOImplicitPuns extends BIOImplicitPuns1 {
    @inline implicit final def BIOTemporal[F[+_, +_]: Temporal2, E, A](self: F[E, A]): BIOSyntax.BIOTemporalOps[F, E, A] = new BIOSyntax.BIOTemporalOps[F, E, A](self)
    @inline implicit final def BIOTemporal[F[+_, +_]: Error2, E, A](self: F[E, A]): BIOSyntax.BIOErrorOps[F, E, A] = new BIOSyntax.BIOErrorOps[F, E, A](self)
    @inline final def BIOTemporal[F[+_, +_]: Temporal2]: Temporal2[F] = implicitly

    @inline implicit final def BIOFork[F[+_, +_]: Fork2, E, A](self: F[E, A]): BIOSyntax.BIOForkOps[F, E, A] = new BIOSyntax.BIOForkOps[F, E, A](self)
    @inline final def BIOFork[F[+_, +_]: Fork2]: Fork2[F] = implicitly
  }
  trait BIOImplicitPuns1 extends BIOImplicitPuns2 {
    @inline implicit final def BIOAsync[F[+_, +_]: Async2, E, A](self: F[E, A]): BIOSyntax.BIOAsyncOps[F, E, A] = new BIOSyntax.BIOAsyncOps[F, E, A](self)
    @inline final def BIOAsync[F[+_, +_]: Async2]: Async2[F] = implicitly
  }
  trait BIOImplicitPuns2 extends BIOImplicitPuns3 {
    @inline implicit final def BIOConcurrent[F[+_, +_]: Concurrent2, E, A](self: F[E, A]): BIOSyntax.BIOConcurrentOps[F, E, A] =
      new BIOSyntax.BIOConcurrentOps[F, E, A](self)
    @inline implicit final def BIOConcurrent[F[+_, +_]: Panic2, E, A](self: F[E, A]): BIOSyntax.BIOPanicOps[F, E, A] =
      new BIOSyntax.BIOPanicOps[F, E, A](self)
    @inline final def BIOConcurrent[F[+_, +_]: Concurrent2]: Concurrent2[F] = implicitly
  }
  trait BIOImplicitPuns3 extends BIOImplicitPuns4 {
    @inline implicit final def BIOParallel[F[+_, +_]: Parallel2, E, A](self: F[E, A]): BIOSyntax.BIOParallelOps[F, E, A] = new BIOSyntax.BIOParallelOps[F, E, A](self)
    @inline implicit final def BIOParallel[F[+_, +_]: Monad2, E, A](self: F[E, A]): BIOSyntax.BIOMonadOps[F, E, A] = new BIOSyntax.BIOMonadOps[F, E, A](self)
    @inline final def BIOParallel[F[+_, +_]: Parallel2]: Parallel2[F] = implicitly
  }
  trait BIOImplicitPuns4 extends BIOImplicitPuns5 {
    @inline implicit final def BIO[F[+_, +_]: IO2, E, A](self: F[E, A]): BIOSyntax.BIOOps[F, E, A] = new BIOSyntax.BIOOps[F, E, A](self)
    /**
      * Shorthand for [[IO3#syncThrowable]]
      *
      * {{{
      *   IO2(println("Hello world!"))
      * }}}
      */
    @inline final def BIO[F[+_, +_], A](effect: => A)(implicit F: IO2[F]): F[Throwable, A] = F.syncThrowable(effect)
    @inline final def BIO[F[+_, +_]: IO2]: IO2[F] = implicitly
  }
  trait BIOImplicitPuns5 extends BIOImplicitPuns6 {
    @inline implicit final def BIOPanic[F[+_, +_]: Panic2, E, A](self: F[E, A]): BIOSyntax.BIOPanicOps[F, E, A] = new BIOSyntax.BIOPanicOps[F, E, A](self)
    @inline final def BIOPanic[F[+_, +_]: Panic2]: Panic2[F] = implicitly
  }
  trait BIOImplicitPuns6 extends BIOImplicitPuns7 {
    @inline implicit final def BIOBracket[F[+_, +_]: Bracket2, E, A](self: F[E, A]): BIOSyntax.BIOBracketOps[F, E, A] = new BIOSyntax.BIOBracketOps[F, E, A](self)
    @inline final def BIOBracket[F[+_, +_]: Bracket2]: Bracket2[F] = implicitly
  }
  trait BIOImplicitPuns7 extends BIOImplicitPuns8 {
    @inline implicit final def BIOError[F[+_, +_]: Error2, E, A](self: F[E, A]): BIOSyntax.BIOErrorOps[F, E, A] =
      new BIOSyntax.BIOErrorOps[F, E, A](self)
    @inline final def BIOError[F[+_, +_]: Error2]: Error2[F] = implicitly
  }
  trait BIOImplicitPuns8 extends BIOImplicitPuns9 {
    @inline implicit final def BIOApplicativeError[F[+_, +_]: ApplicativeError2, E, A](self: F[E, A]): BIOSyntax.BIOApplicativeErrorOps[F, E, A] =
      new BIOSyntax.BIOApplicativeErrorOps[F, E, A](self)
    @inline final def BIOApplicativeError[F[+_, +_]: ApplicativeError2]: ApplicativeError2[F] = implicitly
  }
  trait BIOImplicitPuns9 extends BIOImplicitPuns10 {
    @inline implicit final def BIOGuarantee[F[+_, +_]: Guarantee2, E, A](self: F[E, A]): BIOSyntax.BIOGuaranteeOps[F, E, A] =
      new BIOSyntax.BIOGuaranteeOps[F, E, A](self)
    @inline final def BIOGuarantee[F[+_, +_]: Guarantee2]: Guarantee2[F] = implicitly
  }
  trait BIOImplicitPuns10 extends BIOImplicitPuns11 {
    @inline implicit final def BIOMonad[F[+_, +_]: Monad2, E, A](self: F[E, A]): BIOSyntax.BIOMonadOps[F, E, A] = new BIOSyntax.BIOMonadOps[F, E, A](self)
    @inline final def BIOMonad[F[+_, +_]: Monad2]: Monad2[F] = implicitly
  }
  trait BIOImplicitPuns11 extends BIOImplicitPuns12 {
    @inline implicit final def BIOApplicative[F[+_, +_]: Applicative2, E, A](self: F[E, A]): BIOSyntax.BIOApplicativeOps[F, E, A] =
      new BIOSyntax.BIOApplicativeOps[F, E, A](self)
    @inline final def BIOApplicative[F[+_, +_]: Applicative2]: Applicative2[F] = implicitly
  }
  trait BIOImplicitPuns12 extends BIOImplicitPuns13 {
    @inline implicit final def BIOBifunctor[F[+_, +_]: Bifunctor2, E, A](self: F[E, A]): BIOSyntax.BIOBifunctorOps[F, E, A] =
      new BIOSyntax.BIOBifunctorOps[F, E, A](self)
    @inline implicit final def BIOBifunctor[F[+_, +_]: Functor2, E, A](self: F[E, A]): BIOSyntax.BIOFunctorOps[F, E, A] = new BIOSyntax.BIOFunctorOps[F, E, A](self)
    @inline final def BIOBifunctor[F[+_, +_]: Bifunctor2]: Bifunctor2[F] = implicitly
  }
  trait BIOImplicitPuns13 extends BIOImplicitPuns14 {
    @inline implicit final def BIOFunctor[F[+_, +_]: Functor2, E, A](self: F[E, A]): BIOSyntax.BIOFunctorOps[F, E, A] = new BIOSyntax.BIOFunctorOps[F, E, A](self)
    @inline final def BIOFunctor[F[+_, +_]: Functor2]: Functor2[F] = implicitly
  }
  trait BIOImplicitPuns14 {
    @deprecated("Use Error2", "0.11")
    @inline implicit final def BIOMonadError[F[+_, +_]: Error2, E, A](self: F[E, A]): BIOSyntax.BIOErrorOps[F, E, A] =
      new BIOSyntax.BIOErrorOps[F, E, A](self)
    @deprecated("Use Error2", "0.11")
    @inline final def BIOMonadError[F[+_, +_]: Error2]: Error2[F] = implicitly
  }

}
