package izumi.functional.bio.syntax

import izumi.functional.bio.*
import izumi.functional.bio.syntax.Syntax2.ImplicitPuns
import izumi.fundamentals.platform.language.SourceFilePositionMaterializer

import scala.annotation.unused
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.language.implicitConversions

trait Syntax2 extends ImplicitPuns

object Syntax2 {

  class FunctorOps[+F[+_, +_], +E, +A](protected[this] val r: F[E, A])(implicit protected[this] val F: Functor2[F]) {
    @inline final def map[B](f: A => B): F[E, B] = F.map(r)(f)

    @inline final def as[B](b: => B): F[E, B] = F.map(r)(_ => b)
    @inline final def void: F[E, Unit] = F.void(r)
    @inline final def widen[A1](implicit @unused ev: A <:< A1): F[E, A1] = r.asInstanceOf[F[E, A1]]

    @inline final def fromOptionOr[B](valueOnNone: => B)(implicit ev: A <:< Option[B]): F[E, B] = F.fromOptionOr(valueOnNone, widen)
  }

  final class BifunctorOps[+F[+_, +_], +E, +A](protected[this] val r: F[E, A])(implicit protected[this] val F: Bifunctor2[F]) {
    @inline final def leftMap[E2](f: E => E2): F[E2, A] = F.leftMap(r)(f)
    @inline final def bimap[E2, B](f: E => E2, g: A => B): F[E2, B] = F.bimap(r)(f, g)

    @inline final def widenError[E1 >: E]: F[E1, A] = r
    @inline final def widenBoth[E1 >: E, A1](implicit @unused ev2: A <:< A1): F[E1, A1] = r.asInstanceOf[F[E1, A1]]
  }

  class ApplicativeOps[F[+_, +_], +E, +A](override protected[this] val r: F[E, A])(implicit override protected[this] val F: Applicative2[F]) extends FunctorOps(r) {

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

  class GuaranteeOps[F[+_, +_], +E, +A](override protected[this] val r: F[E, A])(implicit override protected[this] val F: Guarantee2[F]) extends ApplicativeOps(r) {
    @inline final def guarantee(cleanup: F[Nothing, Unit]): F[E, A] = F.guarantee(r, cleanup)
  }

  class ApplicativeErrorOps[F[+_, +_], +E, +A](override protected[this] val r: F[E, A])(implicit override protected[this] val F: ApplicativeError2[F])
    extends GuaranteeOps(r) {
    @inline final def leftMap[E2](f: E => E2): F[E2, A] = F.leftMap(r)(f)
    @inline final def bimap[E2, B](f: E => E2, g: A => B): F[E2, B] = F.bimap(r)(f, g)

    @inline final def orElse[E2, A1 >: A](r2: => F[E2, A1]): F[E2, A1] = F.orElse(r, r2)
    @inline final def leftMap2[E2, A1 >: A, E3](r2: => F[E2, A1])(f: (E, E2) => E3): F[E3, A1] = F.leftMap2(r, r2)(f)

    @inline final def widenError[E1 >: E]: F[E1, A] = r
    @inline final def widenBoth[E1 >: E, A1](implicit @unused ev2: A <:< A1): F[E1, A1] = r.asInstanceOf[F[E1, A1]]
  }

  final class MonadOps[F[+_, +_], +E, +A](override protected[this] val r: F[E, A])(implicit override protected[this] val F: Monad2[F]) extends ApplicativeOps(r) {
    @inline final def flatMap[E1 >: E, B](f0: A => F[E1, B]): F[E1, B] = F.flatMap[Any, E1, A, B](r)(f0)
    @inline final def tap[E1 >: E, B](f0: A => F[E1, Unit]): F[E1, A] = F.tap(r, f0)

    @inline final def flatten[E1 >: E, A1](implicit ev: A <:< F[E1, A1]): F[E1, A1] = F.flatten(r.widen)

    @inline final def iterateWhile(p: A => Boolean): F[E, A] = F.iterateWhile(r)(p)
    @inline final def iterateUntil(p: A => Boolean): F[E, A] = F.iterateUntil(r)(p)

    @inline final def fromOptionF[E1 >: E, B](fallbackOnNone: => F[E1, B])(implicit ev: A <:< Option[B]): F[E1, B] = F.fromOptionF(fallbackOnNone, r.widen)
  }

  class ErrorOps[F[+_, +_], +E, +A](override protected[this] val r: F[E, A])(implicit override protected[this] val F: Error2[F]) extends ApplicativeErrorOps(r) {
    // duplicated from MonadOps
    @inline final def flatMap[E1 >: E, B](f0: A => F[E1, B]): F[E1, B] = F.flatMap[Any, E1, A, B](r)(f0)
    @inline final def tap[E1 >: E, B](f0: A => F[E1, Unit]): F[E1, A] = F.tap(r, f0)

    @inline final def flatten[E1 >: E, A1](implicit ev: A <:< F[E1, A1]): F[E1, A1] = F.flatten(r.widen)

    @inline final def iterateWhile(p: A => Boolean): F[E, A] = F.iterateWhile(r)(p)
    @inline final def iterateUntil(p: A => Boolean): F[E, A] = F.iterateUntil(r)(p)

    @inline final def fromOptionF[E1 >: E, B](fallbackOnNone: => F[E1, B])(implicit ev: A <:< Option[B]): F[E1, B] = F.fromOptionF(fallbackOnNone, r.widen)
    // duplicated from MonadOps

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
    @inline final def fromOption[E1 >: E, A1](errorOnNone: => E1)(implicit ev1: A <:< Option[A1]): F[E1, A1] = F.fromOption(errorOnNone, r.widen)

    @inline final def retryWhile(f: E => Boolean): F[E, A] = F.retryWhile(r)(f)
    @inline final def retryWhileF(f: E => F[Nothing, Boolean]): F[E, A] = F.retryWhileF(r)(f)

    @inline final def retryUntil(f: E => Boolean): F[E, A] = F.retryUntil(r)(f)
    @inline final def retryUntilF(f: E => F[Nothing, Boolean]): F[E, A] = F.retryUntilF(r)(f)

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

  class BracketOps[F[+_, +_], +E, +A](override protected[this] val r: F[E, A])(implicit override protected[this] val F: Bracket2[F]) extends ErrorOps(r) {
    @inline final def bracket[E1 >: E, B](release: A => F[Nothing, Unit])(use: A => F[E1, B]): F[E1, B] = F.bracket(r: F[E1, A])(release)(use)

    @inline final def bracketCase[E1 >: E, B](release: (A, Exit[E1, B]) => F[Nothing, Unit])(use: A => F[E1, B]): F[E1, B] = F.bracketCase(r: F[E1, A])(release)(use)
    @inline final def guaranteeCase(cleanup: Exit[E, A] => F[Nothing, Unit]): F[E, A] = F.guaranteeCase(r, cleanup)

    @inline final def bracketOnFailure[E1 >: E, B](cleanupOnFailure: (A, Exit.Failure[E1]) => F[Nothing, Unit])(use: A => F[E1, B]): F[E1, B] =
      F.bracketOnFailure(r: F[E1, A])(cleanupOnFailure)(use)
    @inline final def guaranteeOnFailure(cleanupOnFailure: Exit.Failure[E] => F[Nothing, Unit]): F[E, A] = F.guaranteeOnFailure(r, cleanupOnFailure)
    @inline final def guaranteeOnInterrupt(cleanupOnInterruption: Exit.Interruption => F[Nothing, Unit]): F[E, A] = F.guaranteeOnInterrupt(r, cleanupOnInterruption)
    @inline final def guaranteeExceptOnInterrupt(
      cleanupOnNonInterruption: Either[Exit.Termination, Either[Exit.Error[E], Exit.Success[A]]] => F[Nothing, Unit]
    ): F[E, A] =
      F.guaranteeExceptOnInterrupt(r, cleanupOnNonInterruption)
  }

  class PanicOps[F[+_, +_], +E, +A](override protected[this] val r: F[E, A])(implicit override protected[this] val F: Panic2[F]) extends BracketOps(r) {
    @inline final def sandbox: F[Exit.Failure[E], A] = F.sandbox(r)
    @inline final def sandboxExit: F[Nothing, Exit[E, A]] = F.redeemPure(F.sandbox(r))(identity, Exit.Success(_))

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
    @inline final def uninterruptible: F[E, A] = F.uninterruptible(r)
  }

  class IOOps[F[+_, +_], +E, +A](override protected[this] val r: F[E, A])(implicit override protected[this] val F: IO2[F]) extends PanicOps(r) {
    @inline final def bracketAuto[E1 >: E, B](use: A => F[E1, B])(implicit ev: A <:< AutoCloseable): F[E1, B] =
      F.bracket[Any, E1, A, B](r)(c => F.sync(c.close()))(use)
  }

  class ParallelOps[F[+_, +_], +E, +A](protected[this] val r: F[E, A])(implicit protected[this] val F: Parallel2[F]) {
    @inline final def zipWithPar[E1 >: E, B, C](that: F[E1, B])(f: (A, B) => C): F[E1, C] = F.zipWithPar(r, that)(f)
    @inline final def zipPar[E1 >: E, B](that: F[E1, B]): F[E1, (A, B)] = F.zipPar(r, that)
    @inline final def zipParLeft[E1 >: E, B](that: F[E1, B]): F[E1, A] = F.zipParLeft(r, that)
    @inline final def zipParRight[E1 >: E, B](that: F[E1, B]): F[E1, B] = F.zipParRight(r, that)
  }
  final class ConcurrentOps[F[+_, +_], +E, +A](override protected[this] val r: F[E, A])(implicit override protected[this] val F: Concurrent2[F])
    extends ParallelOps(r)(F) {
    @inline final def race[E1 >: E, A1 >: A](that: F[E1, A1]): F[E1, A1] = F.race(r, that)
    @inline final def racePairUnsafe[E1 >: E, A1 >: A](
      that: F[E1, A1]
    ): F[E1, Either[(Exit[E1, A], Fiber2[F, E1, A1]), (Fiber2[F, E1, A], Exit[E1, A1])]] = F.racePairUnsafe(r, that)
  }
  class AsyncOps[F[+_, +_], +E, +A](override protected[this] val r: F[E, A])(implicit override protected[this] val F: Async2[F]) extends IOOps(r) {
    @inline final def zipWithPar[E1 >: E, B, C](that: F[E1, B])(f: (A, B) => C): F[E1, C] = F.zipWithPar(r, that)(f)
    @inline final def zipPar[E1 >: E, B](that: F[E1, B]): F[E1, (A, B)] = F.zipPar(r, that)
    @inline final def zipParLeft[E1 >: E, B](that: F[E1, B]): F[E1, A] = F.zipParLeft(r, that)
    @inline final def zipParRight[E1 >: E, B](that: F[E1, B]): F[E1, B] = F.zipParRight(r, that)

    @inline final def race[E1 >: E, A1 >: A](that: F[E1, A1]): F[E1, A1] = F.race(r, that)
    @inline final def racePairUnsafe[E1 >: E, A1 >: A](
      that: F[E1, A1]
    ): F[E1, Either[(Exit[E1, A], Fiber2[F, E1, A1]), (Fiber2[F, E1, A], Exit[E1, A1])]] = F.racePairUnsafe(r, that)
  }

  final class TemporalOps[F[+_, +_], +E, +A](protected[this] val r: F[E, A])(implicit protected[this] val F: Temporal2[F]) {
    @inline final def retryOrElse[A2 >: A, E2](duration: FiniteDuration, orElse: E => F[E2, A2]): F[E2, A2] = F.retryOrElseUntil[Any, E, A2, E2](r)(duration, orElse)
    @inline final def repeatUntil[E2 >: E, A2](tooManyAttemptsError: => E2, sleep: FiniteDuration, maxAttempts: Int)(implicit ev: A <:< Option[A2]): F[E2, A2] =
      F.repeatUntil[Any, E2, A2](new FunctorOps(r)(F.InnerF).widen)(tooManyAttemptsError, sleep, maxAttempts)

    @inline final def timeout(duration: Duration): F[E, Option[A]] = F.timeout(duration)(r)
    @inline final def timeoutFail[E1 >: E](e: => E1)(duration: Duration): F[E1, A] = F.timeoutFail(duration)(e, r)
  }

  final class ForkOps[F[+_, +_], +E, +A](private val r: F[E, A])(implicit private val F: Fork2[F]) {
    @inline final def fork: F[Nothing, Fiber2[F, E, A]] = F.fork(r)
  }

  trait ImplicitPuns extends ImplicitPuns1 {
    @inline implicit final def Temporal2[F[+_, +_]: Temporal2, E, A](self: F[E, A]): TemporalOps[F, E, A] = new TemporalOps[F, E, A](self)
    @inline implicit final def Temporal2[F[+_, +_]: Error2, E, A](self: F[E, A]): ErrorOps[F, E, A] = new ErrorOps[F, E, A](self)
    @inline final def Temporal2[F[+_, +_]: Temporal2]: Temporal2[F] = implicitly

    @inline implicit final def Fork2[F[+_, +_]: Fork2, E, A](self: F[E, A]): ForkOps[F, E, A] = new ForkOps[F, E, A](self)
    @inline final def Fork2[F[+_, +_]: Fork2]: Fork2[F] = implicitly
  }
  trait ImplicitPuns1 extends ImplicitPuns2 {
    @inline implicit final def Async2[F[+_, +_]: Async2, E, A](self: F[E, A]): AsyncOps[F, E, A] = new AsyncOps[F, E, A](self)
    @inline final def Async2[F[+_, +_]: Async2]: Async2[F] = implicitly
  }
  trait ImplicitPuns2 extends ImplicitPuns3 {
    @inline implicit final def Concurrent2[F[+_, +_]: Concurrent2, E, A](self: F[E, A]): ConcurrentOps[F, E, A] = new ConcurrentOps[F, E, A](self)
    @inline implicit final def Concurrent2[F[+_, +_]: Panic2, E, A](self: F[E, A]): PanicOps[F, E, A] = new PanicOps[F, E, A](self)
    @inline final def Concurrent2[F[+_, +_]: Concurrent2]: Concurrent2[F] = implicitly
  }
  trait ImplicitPuns3 extends ImplicitPuns4 {
    @inline implicit final def Parallel2[F[+_, +_]: Parallel2, E, A](self: F[E, A]): ParallelOps[F, E, A] = new ParallelOps[F, E, A](self)
    @inline implicit final def Parallel2[F[+_, +_]: Monad2, E, A](self: F[E, A]): MonadOps[F, E, A] = new MonadOps[F, E, A](self)
    @inline final def Parallel2[F[+_, +_]: Parallel2]: Parallel2[F] = implicitly
  }
  trait ImplicitPuns4 extends ImplicitPuns5 {
    @inline implicit final def IO2[F[+_, +_]: IO2, E, A](self: F[E, A]): IOOps[F, E, A] = new IOOps[F, E, A](self)
    /**
      * Shorthand for [[IO3#syncThrowable]]
      *
      * {{{
      *   IO2(println("Hello world!"))
      * }}}
      */
    @inline final def IO2[F[+_, +_], A](effect: => A)(implicit F: IO2[F]): F[Throwable, A] = F.syncThrowable(effect)
    @inline final def IO2[F[+_, +_]: IO2]: IO2[F] = implicitly
  }
  trait ImplicitPuns5 extends ImplicitPuns6 {
    @inline implicit final def Panic2[F[+_, +_]: Panic2, E, A](self: F[E, A]): PanicOps[F, E, A] = new PanicOps[F, E, A](self)
    @inline final def Panic2[F[+_, +_]: Panic2]: Panic2[F] = implicitly
  }
  trait ImplicitPuns6 extends ImplicitPuns7 {
    @inline implicit final def Bracket2[F[+_, +_]: Bracket2, E, A](self: F[E, A]): BracketOps[F, E, A] = new BracketOps[F, E, A](self)
    @inline final def Bracket2[F[+_, +_]: Bracket2]: Bracket2[F] = implicitly
  }
  trait ImplicitPuns7 extends ImplicitPuns8 {
    @inline implicit final def Error2[F[+_, +_]: Error2, E, A](self: F[E, A]): ErrorOps[F, E, A] = new ErrorOps[F, E, A](self)
    @inline final def Error2[F[+_, +_]: Error2]: Error2[F] = implicitly
  }
  trait ImplicitPuns8 extends ImplicitPuns9 {
    @inline implicit final def ApplicativeError2[F[+_, +_]: ApplicativeError2, E, A](self: F[E, A]): ApplicativeErrorOps[F, E, A] = new ApplicativeErrorOps[F, E, A](self)
    @inline final def ApplicativeError2[F[+_, +_]: ApplicativeError2]: ApplicativeError2[F] = implicitly
  }
  trait ImplicitPuns9 extends ImplicitPuns10 {
    @inline implicit final def Guarantee2[F[+_, +_]: Guarantee2, E, A](self: F[E, A]): GuaranteeOps[F, E, A] = new GuaranteeOps[F, E, A](self)
    @inline final def Guarantee2[F[+_, +_]: Guarantee2]: Guarantee2[F] = implicitly
  }
  trait ImplicitPuns10 extends ImplicitPuns11 {
    @inline implicit final def Monad2[F[+_, +_]: Monad2, E, A](self: F[E, A]): MonadOps[F, E, A] = new MonadOps[F, E, A](self)
    @inline final def Monad2[F[+_, +_]: Monad2]: Monad2[F] = implicitly
  }
  trait ImplicitPuns11 extends ImplicitPuns12 {
    @inline implicit final def Applicative2[F[+_, +_]: Applicative2, E, A](self: F[E, A]): ApplicativeOps[F, E, A] = new ApplicativeOps[F, E, A](self)
    @inline final def Applicative2[F[+_, +_]: Applicative2]: Applicative2[F] = implicitly
  }
  trait ImplicitPuns12 extends ImplicitPuns13 {
    @inline implicit final def Bifunctor2[F[+_, +_]: Bifunctor2, E, A](self: F[E, A]): BifunctorOps[F, E, A] = new BifunctorOps[F, E, A](self)
    @inline implicit final def Bifunctor2[F[+_, +_]: Functor2, E, A](self: F[E, A]): FunctorOps[F, E, A] = new FunctorOps[F, E, A](self)
    @inline final def Bifunctor2[F[+_, +_]: Bifunctor2]: Bifunctor2[F] = implicitly
  }
  trait ImplicitPuns13 {
    @inline implicit final def Functor2[F[+_, +_]: Functor2, E, A](self: F[E, A]): FunctorOps[F, E, A] = new FunctorOps[F, E, A](self)
    @inline final def Functor2[F[+_, +_]: Functor2]: Functor2[F] = implicitly
  }

}
