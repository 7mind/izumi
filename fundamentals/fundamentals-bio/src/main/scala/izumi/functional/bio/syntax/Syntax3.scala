package izumi.functional.bio.syntax

import cats.data.Kleisli
import izumi.functional.bio.syntax.Syntax3.ImplicitPuns
import izumi.functional.bio.{Applicative3, ApplicativeError3, Arrow3, ArrowChoice3, Ask3, Async3, Bifunctor3, Bracket3, Concurrent3, Error3, Exit, Fiber3, Fork3, Functor3, Guarantee3, IO3, Local3, Monad3, MonadAsk3, Panic3, Parallel3, Profunctor3, Temporal3, WithFilter}
import izumi.fundamentals.platform.language.{SourceFilePositionMaterializer, unused}

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.language.implicitConversions

/**
  * All implicit syntax in BIO is available automatically without wildcard imports
  * with the help of so-called "implicit punning", as in the following example:
  *
  * {{{
  *   import izumi.functional.bio.Monad2
  *
  *   def loop[F[+_, +_]: Monad2]: F[Nothing, Nothing] = {
  *     val unitEffect: F[Nothing, Unit] = Monad2[F].unit
  *     unitEffect.flatMap(loop)
  *   }
  * }}}
  *
  * Note that a `.flatMap` method is available on the `unitEffect` value of an abstract type parameter `F`,
  * even though we did not import any syntax implicits using a wildcard import.
  *
  * The `flatMap` method was added by the implicit punning on the `Monad2` name.
  * In short, implicit punning just means that instead of creating a companion object for a type with the same name as the type,
  * we create "companion" implicit conversions with the same name. So that whenever you import the type,
  * you are also always importing the syntax-providing implicit conversions.
  *
  * This happens to be a great fit for Tagless Final Style, since nearly all TF code will import the names of the used typeclasses.
  *
  * Implicit Punning for typeclass syntax relieves the programmer from having to manually import syntax implicits in every file in their codebase.
  *
  * @note The order of conversions is such to allow otherwise conflicting type classes to not conflict,
  *       e.g. code using constraints such as `def x[F[+_, +_]: Functor2: Applicative2: Monad2]` will compile and run
  *       normally when using syntax, despite ambiguity of implicits caused by all 3 implicits inheriting from Functor2.
  *       This is because, due to the priority order being from most-specific to least-specific, the `Monad2` syntax
  *       will be used in such a case, where the `Monad2[F]` implicit is actually unambiguous.
  */
trait Syntax3 extends ImplicitPuns {
  /**
    * A convenient dependent summoner for BIO hierarchy.
    * Auto-narrows to the most powerful available class:
    *
    * {{{
    *   import izumi.functional.bio.{F, Temporal2}
    *
    *   def y[F[+_, +_]: Temporal2] = {
    *     F.timeout(5.seconds)(F.forever(F.unit))
    *   }
    * }}}
    */
//  def F[FR[-_, +_, +_]](implicit FR: Functor3[FR]): FR.type = FR
//
//  def F[FR[-_, +_, +_], A](effect: => A)(implicit FR: IO3[FR]): FR.Or[Throwable, A] = FR.syncThrowable(effect)
}

object Syntax3 {

  class FunctorOps[+FR[-_, +_, +_], -R, +E, +A](protected[this] val r: FR[R, E, A])(implicit protected[this] val F: Functor3[FR]) {
    @inline final def map[B](f: A => B): FR[R, E, B] = F.map(r)(f)

    @inline final def as[B](b: => B): FR[R, E, B] = F.map(r)(_ => b)
    @inline final def void: FR[R, E, Unit] = F.void(r)
    @inline final def widen[A1](implicit @unused ev: A <:< A1): FR[R, E, A1] = r.asInstanceOf[FR[R, E, A1]]

    @inline final def fromOptionOr[B](valueOnNone: => B)(implicit ev: A <:< Option[B]): FR[R, E, B] = F.fromOptionOr(valueOnNone, widen)
  }

  final class BifunctorOps[+FR[-_, +_, +_], -R, +E, +A](protected[this] val r: FR[R, E, A])(implicit protected[this] val F: Bifunctor3[FR]) {
    @inline final def leftMap[E2](f: E => E2): FR[R, E2, A] = F.leftMap(r)(f)
    @inline final def bimap[E2, B](f: E => E2, g: A => B): FR[R, E2, B] = F.bimap(r)(f, g)

    @inline final def widenError[E1 >: E]: FR[R, E1, A] = r
    @inline final def widenBoth[E1 >: E, A1](implicit @unused ev2: A <:< A1): FR[R, E1, A1] = r.asInstanceOf[FR[R, E1, A1]]
  }

  class ApplicativeOps[FR[-_, +_, +_], -R, +E, +A](override protected[this] val r: FR[R, E, A])(implicit override protected[this] val F: Applicative3[FR])
    extends FunctorOps(r) {

    /** execute two operations in order, return result of second operation */
    @inline final def *>[R1 <: R, E1 >: E, B](f0: => FR[R1, E1, B]): FR[R1, E1, B] = F.*>(r, f0)

    /** execute two operations in order, same as `*>`, but return result of first operation */
    @inline final def <*[R1 <: R, E1 >: E, B](f0: => FR[R1, E1, B]): FR[R1, E1, A] = F.<*(r, f0)

    /** execute two operations in order, return result of both operations */
    @inline final def zip[R1 <: R, E1 >: E, B, C](r2: => FR[R1, E1, B]): FR[R1, E1, (A, B)] = F.map2(r, r2)(_ -> _)

    /** execute two operations in order, map their results */
    @inline final def map2[R1 <: R, E1 >: E, B, C](r2: => FR[R1, E1, B])(f: (A, B) => C): FR[R1, E1, C] = F.map2(r, r2)(f)

    @inline final def forever: FR[R, E, Nothing] = F.forever(r)
  }

  class GuaranteeOps[FR[-_, +_, +_], -R, +E, +A](override protected[this] val r: FR[R, E, A])(implicit override protected[this] val F: Guarantee3[FR])
    extends ApplicativeOps(r) {
    @inline final def guarantee[R1 <: R](cleanup: FR[R1, Nothing, Unit]): FR[R1, E, A] = F.guarantee(r, cleanup)
  }

  final class MonadOps[FR[-_, +_, +_], -R, +E, +A](override protected[this] val r: FR[R, E, A])(implicit override protected[this] val F: Monad3[FR])
    extends ApplicativeOps(r) {
    @inline final def flatMap[R1 <: R, E1 >: E, B](f0: A => FR[R1, E1, B]): FR[R1, E1, B] = F.flatMap[R1, E1, A, B](r)(f0)
    @inline final def tap[R1 <: R, E1 >: E, B](f0: A => FR[R1, E1, Unit]): FR[R1, E1, A] = F.tap(r, f0)

    @inline final def flatten[R1 <: R, E1 >: E, A1](implicit ev: A <:< FR[R1, E1, A1]): FR[R1, E1, A1] = F.flatten(F.widen(r))

    @inline final def iterateWhile(p: A => Boolean): FR[R, E, A] = F.iterateWhile(r)(p)
    @inline final def iterateUntil(p: A => Boolean): FR[R, E, A] = F.iterateUntil(r)(p)

    @inline final def fromOptionF[R1 <: R, E1 >: E, B](fallbackOnNone: => FR[R1, E1, B])(implicit ev: A <:< Option[B]): FR[R1, E1, B] =
      F.fromOptionF(fallbackOnNone, r.widen)
  }

  class ApplicativeErrorOps[FR[-_, +_, +_], -R, +E, +A](
    override protected[this] val r: FR[R, E, A]
  )(implicit override protected[this] val F: ApplicativeError3[FR]
  ) extends GuaranteeOps(r) {
    @inline final def leftMap[E2](f: E => E2): FR[R, E2, A] = F.leftMap(r)(f)
    @inline final def bimap[E2, B](f: E => E2, g: A => B): FR[R, E2, B] = F.bimap(r)(f, g)

    @inline final def orElse[R1 <: R, E2, A1 >: A](r2: => FR[R1, E2, A1]): FR[R1, E2, A1] = F.orElse(r, r2)
    @inline final def leftMap2[R1 <: R, E2, A1 >: A, E3](r2: => FR[R1, E2, A1])(f: (E, E2) => E3): FR[R1, E3, A1] = F.leftMap2(r, r2)(f)

    @inline final def widenError[E1 >: E]: FR[R, E1, A] = r
    @inline final def widenBoth[E1 >: E, A1](implicit @unused ev2: A <:< A1): FR[R, E1, A1] = r.asInstanceOf[FR[R, E1, A1]]
  }

  class ErrorOps[FR[-_, +_, +_], -R, +E, +A](override protected[this] val r: FR[R, E, A])(implicit override protected[this] val F: Error3[FR])
    extends ApplicativeErrorOps(r) {
    // duplicated from MonadOps
    @inline final def flatMap[R1 <: R, E1 >: E, B](f0: A => FR[R1, E1, B]): FR[R1, E1, B] = F.flatMap[R1, E1, A, B](r)(f0)
    @inline final def tap[R1 <: R, E1 >: E, B](f0: A => FR[R1, E1, Unit]): FR[R1, E1, A] = F.tap(r, f0)

    @inline final def flatten[R1 <: R, E1 >: E, A1](implicit ev: A <:< FR[R1, E1, A1]): FR[R1, E1, A1] = F.flatten(F.widen(r))

    @inline final def iterateWhile(p: A => Boolean): FR[R, E, A] = F.iterateWhile(r)(p)
    @inline final def iterateUntil(p: A => Boolean): FR[R, E, A] = F.iterateUntil(r)(p)

    @inline final def fromOptionF[R1 <: R, E1 >: E, B](fallbackOnNone: => FR[R1, E1, B])(implicit ev: A <:< Option[B]): FR[R1, E1, B] =
      F.fromOptionF(fallbackOnNone, r.widen)
    // duplicated from MonadOps

    @inline final def catchAll[R1 <: R, E2, A2 >: A](h: E => FR[R1, E2, A2]): FR[R1, E2, A2] = F.catchAll[R1, E, A2, E2](r)(h)
    @inline final def catchSome[R1 <: R, E1 >: E, A2 >: A](h: PartialFunction[E, FR[R1, E1, A2]]): FR[R1, E1, A2] = F.catchSome[R1, E, A2, E1](r)(h)

    @inline final def redeem[R1 <: R, E2, B](err: E => FR[R1, E2, B], succ: A => FR[R1, E2, B]): FR[R1, E2, B] = F.redeem[R1, E, A, E2, B](r)(err, succ)
    @inline final def redeemPure[B](err: E => B, succ: A => B): FR[R, Nothing, B] = F.redeemPure(r)(err, succ)

    @inline final def attempt: FR[R, Nothing, Either[E, A]] = F.attempt(r)

    @inline final def tapError[R1 <: R, E1 >: E](f: E => FR[R1, E1, Unit]): FR[R1, E1, A] = F.tapError[R1, E, A, E1](r)(f)

    @inline final def leftFlatMap[R1 <: R, E2](f: E => FR[R1, Nothing, E2]): FR[R1, E2, A] = F.leftFlatMap[R1, E, A, E2](r)(f)
    @inline final def flip: FR[R, A, E] = F.flip(r)

    @inline final def tapBoth[R1 <: R, E1 >: E, E2 >: E1](err: E => FR[R1, E1, Unit])(succ: A => FR[R1, E2, Unit]): FR[R1, E2, A] = F.tapBoth[R1, E, A, E2](r)(err, succ)

    @inline final def fromEither[R1 <: R, E1 >: E, A1](implicit ev: A <:< Either[E1, A1]): FR[R1, E1, A1] = F.flatMap[R1, E1, A, A1](r)(F.fromEither[E1, A1](_))
    @inline final def fromOption[R1 <: R, E1 >: E, A1](errorOnNone: => E1)(implicit ev1: A <:< Option[A1]): FR[R1, E1, A1] = F.fromOption(errorOnNone, r.widen)

    @inline final def retryWhile(f: E => Boolean): FR[R, E, A] = F.retryWhile(r)(f)
    @inline final def retryWhileF[R1 <: R](f: E => FR[R1, Nothing, Boolean]): FR[R1, E, A] = F.retryWhileF(r)(f)

    @inline final def retryUntil(f: E => Boolean): FR[R, E, A] = F.retryUntil(r)(f)
    @inline final def retryUntilF[R1 <: R](f: E => FR[R1, Nothing, Boolean]): FR[R1, E, A] = F.retryUntilF(r)(f)

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
    @inline final def withFilter[E1 >: E](predicate: A => Boolean)(implicit filter: WithFilter[E1], pos: SourceFilePositionMaterializer): FR[R, E1, A] =
      F.withFilter[R, E1, A](r)(predicate)
  }

  class BracketOps[FR[-_, +_, +_], -R, +E, +A](override protected[this] val r: FR[R, E, A])(implicit override protected[this] val F: Bracket3[FR]) extends ErrorOps(r) {
    @inline final def bracket[R1 <: R, E1 >: E, B](release: A => FR[R1, Nothing, Unit])(use: A => FR[R1, E1, B]): FR[R1, E1, B] =
      F.bracket(r: FR[R1, E1, A])(release)(use)

    @inline final def bracketCase[R1 <: R, E1 >: E, B](release: (A, Exit[E1, B]) => FR[R1, Nothing, Unit])(use: A => FR[R1, E1, B]): FR[R1, E1, B] =
      F.bracketCase(r: FR[R1, E1, A])(release)(use)
    @inline final def guaranteeCase[R1 <: R](cleanup: Exit[E, A] => FR[R1, Nothing, Unit]): FR[R1, E, A] = F.guaranteeCase(r, cleanup)

    @inline final def bracketOnFailure[R1 <: R, E1 >: E, B](cleanupOnFailure: (A, Exit.Failure[E1]) => FR[R1, Nothing, Unit])(use: A => FR[R1, E1, B]): FR[R1, E1, B] =
      F.bracketOnFailure(r: FR[R1, E1, A])(cleanupOnFailure)(use)
    @inline final def guaranteeOnFailure[R1 <: R](cleanupOnFailure: Exit.Failure[E] => FR[R1, Nothing, Unit]): FR[R1, E, A] = F.guaranteeOnFailure(r, cleanupOnFailure)
    @inline final def guaranteeOnInterrupt[R1 <: R](cleanupOnInterruption: Exit.Interruption => FR[R1, Nothing, Unit]): FR[R1, E, A] =
      F.guaranteeOnInterrupt(r, cleanupOnInterruption)
  }

  class PanicOps[FR[-_, +_, +_], -R, +E, +A](override protected[this] val r: FR[R, E, A])(implicit override protected[this] val F: Panic3[FR]) extends BracketOps(r) {
    @inline final def sandbox: FR[R, Exit.Failure[E], A] = F.sandbox(r)
    @inline final def sandboxExit: FR[R, Nothing, Exit[E, A]] = F.redeemPure(F.sandbox(r))(identity, Exit.Success(_))

    /**
      * Catch all _defects_ in this effect and convert them to Throwable
      * Example:
      *
      * {{{
      *   F.pure(1)
      *     .map(_ => ???)
      *     .sandboxThrowable
      *     .catchAll(_ => BIO(println("Caught error!")))
      * }}}
      */
    @inline final def sandboxToThrowable(implicit ev: E <:< Throwable): FR[R, Throwable, A] =
      F.leftMap(F.sandbox(r))(_.toThrowable)

    /** Convert Throwable typed error into a defect */
    @inline final def orTerminate(implicit ev: E <:< Throwable): FR[R, Nothing, A] = F.catchAll(r)(F.terminate(_))
  }

  class IOOps[FR[-_, +_, +_], -R, +E, +A](override protected[this] val r: FR[R, E, A])(implicit override protected[this] val F: IO3[FR]) extends PanicOps(r) {
    @inline final def bracketAuto[R1 <: R, E1 >: E, B](use: A => FR[R1, E1, B])(implicit ev: A <:< AutoCloseable): FR[R1, E1, B] =
      F.bracket(r: FR[R1, E1, A])(c => F.sync(c.close()))(use)
  }

  class ParallelOps[FR[-_, +_, +_], -R, +E, +A](protected[this] val r: FR[R, E, A])(implicit protected[this] val F: Parallel3[FR]) {
    @inline final def zipWithPar[R1 <: R, E1 >: E, B, C](that: FR[R1, E1, B])(f: (A, B) => C): FR[R1, E1, C] = F.zipWithPar(r, that)(f)
    @inline final def zipPar[R1 <: R, E1 >: E, B](that: FR[R1, E1, B]): FR[R1, E1, (A, B)] = F.zipPar(r, that)
    @inline final def zipParLeft[R1 <: R, E1 >: E, B](that: FR[R1, E1, B]): FR[R1, E1, A] = F.zipParLeft(r, that)
    @inline final def zipParRight[R1 <: R, E1 >: E, B](that: FR[R1, E1, B]): FR[R1, E1, B] = F.zipParRight(r, that)
  }

  class ConcurrentOps[FR[-_, +_, +_], -R, +E, +A](override protected[this] val r: FR[R, E, A])(implicit override protected[this] val F: Concurrent3[FR])
    extends ParallelOps(r) {
    @inline final def race[R1 <: R, E1 >: E, A1 >: A](that: FR[R1, E1, A1]): FR[R1, E1, A1] = F.race(r, that)
    @inline final def racePair[R1 <: R, E1 >: E, A1 >: A](that: FR[R1, E1, A1]): FR[R1, E1, Either[(A, Fiber3[FR, E1, A1]), (Fiber3[FR, E1, A], A1)]] =
      F.racePair(r, that)
    @inline final def uninterruptible: FR[R, E, A] = F.uninterruptible(r)
  }

  class AsyncOps[FR[-_, +_, +_], -R, +E, +A](override protected[this] val r: FR[R, E, A])(implicit override protected[this] val F: Async3[FR]) extends IOOps(r) {
    @inline final def zipWithPar[R1 <: R, E1 >: E, B, C](that: FR[R1, E1, B])(f: (A, B) => C): FR[R1, E1, C] = F.zipWithPar(r, that)(f)
    @inline final def zipPar[R1 <: R, E1 >: E, B](that: FR[R1, E1, B]): FR[R1, E1, (A, B)] = F.zipPar(r, that)
    @inline final def zipParLeft[R1 <: R, E1 >: E, B](that: FR[R1, E1, B]): FR[R1, E1, A] = F.zipParLeft(r, that)
    @inline final def zipParRight[R1 <: R, E1 >: E, B](that: FR[R1, E1, B]): FR[R1, E1, B] = F.zipParRight(r, that)

    @inline final def race[R1 <: R, E1 >: E, A1 >: A](that: FR[R1, E1, A1]): FR[R1, E1, A1] = F.race(r, that)
    @inline final def racePair[R1 <: R, E1 >: E, A1 >: A](that: FR[R1, E1, A1]): FR[R1, E1, Either[(A, Fiber3[FR, E1, A1]), (Fiber3[FR, E1, A], A1)]] =
      F.racePair(r, that)
    @inline final def uninterruptible: FR[R, E, A] = F.uninterruptible(r)
  }

  final class TemporalOps[FR[-_, +_, +_], -R, +E, +A](protected[this] val r: FR[R, E, A])(implicit protected[this] val F: Temporal3[FR]) {
    @inline final def retryOrElse[R1 <: R, A2 >: A, E2](duration: FiniteDuration, orElse: => FR[R1, E2, A2]): FR[R1, E2, A2] =
      F.retryOrElse[R1, E, A2, E2](r)(duration, orElse)
    @inline final def repeatUntil[E1 >: E, A2](tooManyAttemptsError: => E1, sleep: FiniteDuration, maxAttempts: Int)(implicit ev: A <:< Option[A2]): FR[R, E1, A2] =
      F.repeatUntil[R, E1, A2](new FunctorOps(r)(F.InnerF).widen)(tooManyAttemptsError, sleep, maxAttempts)

    @inline final def timeout(duration: Duration): FR[R, E, Option[A]] = F.timeout(duration)(r)
    @inline final def timeoutFail[E1 >: E](e: E1)(duration: Duration): FR[R, E1, A] = F.timeoutFail(duration)(e, r)
  }

  final class ForkOps[FR[-_, +_, +_], -R, +E, +A](private val r: FR[R, E, A])(implicit private val F: Fork3[FR]) {
    @inline final def fork: FR[R, Nothing, Fiber3[FR, E, A]] = F.fork(r)
  }

  class ProfunctorOps[+FR[-_, +_, +_], -R, +E, +A](protected[this] val r: FR[R, E, A])(implicit protected[this] val F: Profunctor3[FR]) {
    @inline final def dimap[R1, A1](f: R1 => R)(g: A => A1): FR[R1, E, A1] = F.dimap(r)(f)(g)
  }

  class ArrowOps[FR[-_, +_, +_], -R, +E, +A](override protected[this] val r: FR[R, E, A])(implicit override protected[this] val F: Arrow3[FR]) extends ProfunctorOps(r) {
    @inline final def andThen[E1 >: E, A1](g: FR[A, E1, A1]): FR[R, E1, A1] = F.andThen(r, g)
    @inline final def compose[E1 >: E, R1](g: FR[R1, E1, R]): FR[R1, E1, A] = F.andThen(g, r)
  }

  class ArrowChoiceOps[FR[-_, +_, +_], -R, +E, +A](override protected[this] val r: FR[R, E, A])(implicit override protected[this] val F: ArrowChoice3[FR])
    extends ArrowOps(r) {
    @inline final def choice[R1 <: R, E1 >: E, A1 >: A, R2](g: FR[R2, E1, A1]): FR[Either[R1, R2], E1, A1] = F.choice(r, g)
    @inline final def choose[R1 <: R, E1 >: E, R2, A1](g: FR[R2, E1, A1]): FR[Either[R1, R2], E1, Either[A, A1]] = F.choose(r, g)
  }

  final class LocalOps[FR[-_, +_, +_], -R, +E, +A](protected[this] override val r: FR[R, E, A])(implicit override protected[this] val F: Local3[FR])
    extends ArrowChoiceOps(r) {
    @inline final def provide(env: => R): FR[Any, E, A] = F.provide(r)(env)
    @inline final def contramap[R0 <: R](f: R0 => R): FR[R0, E, A] = F.contramap(r)(f)
  }

  final class LocalOpsKleisliSyntax[FR[-_, +_, +_], R, E, A](private val r: FR[R, E, A])(implicit private val F: Local3[FR]) {
    @inline final def toKleisli: Kleisli[FR[Any, E, _], R, A] = F.toKleisli(r)
  }

  trait ImplicitPuns extends ImplicitPuns1 {
    @inline implicit final def Temporal3[FR[-_, +_, +_]: Temporal3, R, E, A](self: FR[R, E, A]): TemporalOps[FR, R, E, A] = new TemporalOps[FR, R, E, A](self)
    @inline implicit final def Temporal3[FR[-_, +_, +_]: Error3, R, E, A](self: FR[R, E, A]): ErrorOps[FR, R, E, A] = new ErrorOps[FR, R, E, A](self)
    @inline final def Temporal3[FR[-_, +_, +_]: Temporal3]: Temporal3[FR] = implicitly

    @inline implicit final def Fork3[FR[-_, +_, +_]: Fork3, R, E, A](self: FR[R, E, A]): ForkOps[FR, R, E, A] = new ForkOps[FR, R, E, A](self)
    @inline final def Fork3[FR[-_, +_, +_]: Fork3]: Fork3[FR] = implicitly
  }
  trait ImplicitPuns1 extends ImplicitPuns2 {
    @inline implicit final def Async3[FR[-_, +_, +_]: Async3, R, E, A](self: FR[R, E, A]): AsyncOps[FR, R, E, A] = new AsyncOps[FR, R, E, A](self)
    @inline final def Async3[FR[-_, +_, +_]: Async3]: Async3[FR] = implicitly
  }
  trait ImplicitPuns2 extends ImplicitPuns3 {
    @inline implicit final def Concurrent3[FR[-_, +_, +_]: Concurrent3, R, E, A](self: FR[R, E, A]): ConcurrentOps[FR, R, E, A] = new ConcurrentOps[FR, R, E, A](self)
    @inline implicit final def Concurrent3[FR[-_, +_, +_]: Panic3, R, E, A](self: FR[R, E, A]): PanicOps[FR, R, E, A] = new PanicOps[FR, R, E, A](self)
    @inline final def Concurrent3[FR[-_, +_, +_]: Concurrent3]: Concurrent3[FR] = implicitly
  }
  trait ImplicitPuns3 extends ImplicitPuns4 {
    @inline implicit final def Parallel3[FR[-_, +_, +_]: Parallel3, R, E, A](self: FR[R, E, A]): ParallelOps[FR, R, E, A] = new ParallelOps[FR, R, E, A](self)
    @inline implicit final def Parallel3[F[-_, +_, +_]: Monad3, R, E, A](self: F[R, E, A]): MonadOps[F, R, E, A] = new MonadOps[F, R, E, A](self)
    @inline final def Parallel3[FR[-_, +_, +_]: Parallel3]: Parallel3[FR] = implicitly
  }
  trait ImplicitPuns4 extends ImplicitPuns5 {
    @inline implicit final def IO3[FR[-_, +_, +_]: IO3, R, E, A](self: FR[R, E, A]): IOOps[FR, R, E, A] = new IOOps[FR, R, E, A](self)
    /**
      * Shorthand for [[IO3#syncThrowable]]
      *
      * {{{
      *   IO3(println("Hello world!"))
      * }}}
      */
    @inline final def IO3[FR[-_, +_, +_], A](effect: => A)(implicit F: IO3[FR]): FR[Any, Throwable, A] = F.syncThrowable(effect)
    @inline final def IO3[FR[-_, +_, +_]: IO3]: IO3[FR] = implicitly
  }
  trait ImplicitPuns5 extends ImplicitPuns6 {
    @inline implicit final def Panic3[FR[-_, +_, +_]: Panic3, R, E, A](self: FR[R, E, A]): PanicOps[FR, R, E, A] = new PanicOps[FR, R, E, A](self)
    @inline final def Panic3[FR[-_, +_, +_]: Panic3]: Panic3[FR] = implicitly
  }
  trait ImplicitPuns6 extends ImplicitPuns7 {
    @inline implicit final def Bracket3[FR[-_, +_, +_]: Bracket3, R, E, A](self: FR[R, E, A]): BracketOps[FR, R, E, A] = new BracketOps[FR, R, E, A](self)
    @inline final def Bracket3[FR[-_, +_, +_]: Bracket3]: Bracket3[FR] = implicitly
  }
  trait ImplicitPuns7 extends ImplicitPuns8 {
    @inline implicit final def Error3[FR[-_, +_, +_]: Error3, R, E, A](self: FR[R, E, A]): ErrorOps[FR, R, E, A] = new ErrorOps[FR, R, E, A](self)
    @inline final def Error3[FR[-_, +_, +_]: Error3]: Error3[FR] = implicitly
  }
  trait ImplicitPuns8 extends ImplicitPuns9 {
    @inline implicit final def ApplicativeError3[FR[-_, +_, +_]: ApplicativeError3, R, E, A](self: FR[R, E, A]): ApplicativeErrorOps[FR, R, E, A] =
      new ApplicativeErrorOps[FR, R, E, A](self)
    @inline final def ApplicativeError3[FR[-_, +_, +_]: ApplicativeError3]: ApplicativeError3[FR] = implicitly
  }
  trait ImplicitPuns9 extends ImplicitPuns10 {
    @inline implicit final def Guarantee3[FR[-_, +_, +_]: Guarantee3, R, E, A](self: FR[R, E, A]): GuaranteeOps[FR, R, E, A] = new GuaranteeOps[FR, R, E, A](self)
    @inline final def Guarantee3[FR[-_, +_, +_]: Guarantee3]: Guarantee3[FR] = implicitly
  }
  trait ImplicitPuns10 extends ImplicitPuns11 {
    @inline implicit final def Monad3[FR[-_, +_, +_]: Monad3, R, E, A](self: FR[R, E, A]): MonadOps[FR, R, E, A] = new MonadOps[FR, R, E, A](self)
    @inline final def Monad3[FR[-_, +_, +_]: Monad3]: Monad3[FR] = implicitly
  }
  trait ImplicitPuns11 extends ImplicitPuns12 {
    @inline implicit final def Applicative3[FR[-_, +_, +_]: Applicative3, R, E, A](self: FR[R, E, A]): ApplicativeOps[FR, R, E, A] = new ApplicativeOps[FR, R, E, A](self)
    @inline final def Applicative3[FR[-_, +_, +_]: Applicative3]: Applicative3[FR] = implicitly
  }
  trait ImplicitPuns12 extends ImplicitPuns13 {
    @inline implicit final def Bifunctor3[FR[-_, +_, +_]: Bifunctor3, R, E, A](self: FR[R, E, A]): BifunctorOps[FR, R, E, A] = new BifunctorOps[FR, R, E, A](self)
    @inline implicit final def Bifunctor3[FR[-_, +_, +_]: Functor3, R, E, A](self: FR[R, E, A]): FunctorOps[FR, R, E, A] = new FunctorOps[FR, R, E, A](self)
    @inline final def Bifunctor3[FR[-_, +_, +_]: Bifunctor3]: Bifunctor3[FR] = implicitly
  }
  trait ImplicitPuns13 extends ImplicitPuns14 {
    @inline implicit final def Functor3[FR[-_, +_, +_]: Functor3, R, E, A](self: FR[R, E, A]): FunctorOps[FR, R, E, A] = new FunctorOps[FR, R, E, A](self)
    @inline final def Functor3[FR[-_, +_, +_]: Functor3]: Functor3[FR] = implicitly
  }
  trait ImplicitPuns14 extends ImplicitPuns15 {
    // Note, as long as these auxilary conversions to Monad/Applicative/Functor syntaxes etc.
    // have the same output type as Monad3/etc conversions above, they will avoid the specificity rule
    // and _will not_ clash (because the outputs are equal, not <:<).
    // If you merge them into `LocalOps with MonadOps`, they _will_ start clashing

    @inline implicit final def Local3[FR[-_, +_, +_]: Local3, R, E, A](self: FR[R, E, A]): LocalOps[FR, R, E, A] = new LocalOps[FR, R, E, A](self)
    @inline implicit final def Local3[FR[-_, +_, +_]: Monad3, R, E, A](self: FR[R, E, A]): MonadOps[FR, R, E, A] = new MonadOps[FR, R, E, A](self)
    @inline implicit final def Local3[FR[-_, +_, +_]: Local3, R, E, A](self: FR[R, E, A])(implicit d: DummyImplicit): LocalOpsKleisliSyntax[FR, R, E, A] =
      new LocalOpsKleisliSyntax[FR, R, E, A](self)
    @inline final def Local3[FR[-_, +_, +_]: Local3]: Local3[FR] = implicitly
  }
  trait ImplicitPuns15 extends ImplicitPuns16 {
    @inline implicit final def MonadAsk3[FR[-_, +_, +_]: Monad3, R, E, A](self: FR[R, E, A]): MonadOps[FR, R, E, A] = new MonadOps[FR, R, E, A](self)
    @inline final def MonadAsk3[FR[-_, +_, +_]: MonadAsk3]: MonadAsk3[FR] = implicitly
  }
  trait ImplicitPuns16 extends ImplicitPuns17 {
    @inline implicit final def Ask3[FR[-_, +_, +_]: Applicative3, R, E, A](self: FR[R, E, A]): ApplicativeOps[FR, R, E, A] = new ApplicativeOps[FR, R, E, A](self)
    @inline final def Ask3[FR[-_, +_, +_]: Ask3]: Ask3[FR] = implicitly
  }
  trait ImplicitPuns17 extends ImplicitPuns18 {
    @inline implicit final def ArrowChoice3[FR[-_, +_, +_]: ArrowChoice3, R, E, A](self: FR[R, E, A]): ArrowChoiceOps[FR, R, E, A] = new ArrowChoiceOps[FR, R, E, A](self)
    @inline implicit final def ArrowChoice3[FR[-_, +_, +_]: Functor3, R, E, A](self: FR[R, E, A]): FunctorOps[FR, R, E, A] = new FunctorOps[FR, R, E, A](self)
    @inline final def ArrowChoice3[FR[-_, +_, +_]: ArrowChoice3]: ArrowChoice3[FR] = implicitly
  }
  trait ImplicitPuns18 extends ImplicitPuns19 {
    @inline implicit final def Arrow3[FR[-_, +_, +_]: Arrow3, R, E, A](self: FR[R, E, A]): ArrowOps[FR, R, E, A] = new ArrowOps[FR, R, E, A](self)
    @inline implicit final def Arrow3[FR[-_, +_, +_]: Functor3, R, E, A](self: FR[R, E, A]): FunctorOps[FR, R, E, A] = new FunctorOps[FR, R, E, A](self)
    @inline final def Arrow3[FR[-_, +_, +_]: Arrow3]: Arrow3[FR] = implicitly
  }
  trait ImplicitPuns19 {
    @inline implicit final def Profunctor3[FR[-_, +_, +_]: Profunctor3, R, E, A](self: FR[R, E, A]): ProfunctorOps[FR, R, E, A] = new ProfunctorOps[FR, R, E, A](self)
    @inline implicit final def Profunctor3[FR[-_, +_, +_]: Functor3, R, E, A](self: FR[R, E, A]): FunctorOps[FR, R, E, A] = new FunctorOps[FR, R, E, A](self)
    @inline final def Profunctor3[FR[-_, +_, +_]: Profunctor3]: Profunctor3[FR] = implicitly
  }

}
