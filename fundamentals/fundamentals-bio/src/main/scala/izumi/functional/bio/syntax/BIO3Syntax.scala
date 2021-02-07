package izumi.functional.bio.syntax

import cats.data.Kleisli
import izumi.functional.bio.{Applicative3, ApplicativeError3, Arrow3, ArrowChoice3, Ask3, Async3, Bifunctor3, Bracket3, Concurrent3, Error3, Exit, Fiber3, Fork3, Functor3, Guarantee3, IO3, Local3, Monad3, Panic3, Parallel3, Profunctor3, Temporal3, WithFilter}
import izumi.fundamentals.platform.language.{SourceFilePositionMaterializer, unused}

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.language.implicitConversions

object BIO3Syntax {

  class BIOFunctor3Ops[+FR[-_, +_, +_], -R, +E, +A](protected[this] val r: FR[R, E, A])(implicit protected[this] val F: Functor3[FR]) {
    @inline final def map[B](f: A => B): FR[R, E, B] = F.map(r)(f)

    @inline final def as[B](b: => B): FR[R, E, B] = F.map(r)(_ => b)
    @inline final def void: FR[R, E, Unit] = F.void(r)
    @inline final def widen[A1](implicit @unused ev: A <:< A1): FR[R, E, A1] = r.asInstanceOf[FR[R, E, A1]]
  }

  final class BIOBifunctor3Ops[+FR[-_, +_, +_], -R, +E, +A](protected[this] val r: FR[R, E, A])(implicit protected[this] val F: Bifunctor3[FR]) {
    @inline final def leftMap[E2](f: E => E2): FR[R, E2, A] = F.leftMap(r)(f)
    @inline final def bimap[E2, B](f: E => E2, g: A => B): FR[R, E2, B] = F.bimap(r)(f, g)

    @inline final def widenError[E1 >: E]: FR[R, E1, A] = r
    @inline final def widenBoth[E1 >: E, A1](implicit @unused ev2: A <:< A1): FR[R, E1, A1] = r.asInstanceOf[FR[R, E1, A1]]
  }

  class BIOApplicative3Ops[FR[-_, +_, +_], -R, +E, +A](override protected[this] val r: FR[R, E, A])(implicit override protected[this] val F: Applicative3[FR])
    extends BIOFunctor3Ops(r) {

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

  class BIOGuarantee3Ops[FR[-_, +_, +_], -R, +E, +A](override protected[this] val r: FR[R, E, A])(implicit override protected[this] val F: Guarantee3[FR])
    extends BIOApplicative3Ops(r) {
    @inline final def guarantee[R1 <: R](cleanup: FR[R1, Nothing, Unit]): FR[R1, E, A] = F.guarantee(r, cleanup)
  }

  final class BIOMonad3Ops[FR[-_, +_, +_], -R, +E, +A](override protected[this] val r: FR[R, E, A])(implicit override protected[this] val F: Monad3[FR])
    extends BIOApplicative3Ops(r) {
    @inline final def flatMap[R1 <: R, E1 >: E, B](f0: A => FR[R1, E1, B]): FR[R1, E1, B] = F.flatMap[R1, E1, A, B](r)(f0)
    @inline final def tap[R1 <: R, E1 >: E, B](f0: A => FR[R1, E1, Unit]): FR[R1, E1, A] = F.flatMap[R1, E1, A, A](r)(a => F.map(f0(a))(_ => a))

    @inline final def flatten[R1 <: R, E1 >: E, A1](implicit ev: A <:< FR[R1, E1, A1]): FR[R1, E1, A1] = F.flatten(F.widen(r))

    @inline final def iterateWhile(p: A => Boolean): FR[R, E, A] =
      F.iterateWhile(r)(p)

    @inline final def iterateUntil(p: A => Boolean): FR[R, E, A] =
      F.iterateUntil(r)(p)
  }

  class BIOApplicativeError3Ops[FR[-_, +_, +_], -R, +E, +A](
    override protected[this] val r: FR[R, E, A]
  )(implicit override protected[this] val F: ApplicativeError3[FR]
  ) extends BIOGuarantee3Ops(r) {
    @inline final def leftMap[E2](f: E => E2): FR[R, E2, A] = F.leftMap(r)(f)
    @inline final def bimap[E2, B](f: E => E2, g: A => B): FR[R, E2, B] = F.bimap(r)(f, g)

    @inline final def orElse[R1 <: R, E2, A1 >: A](r2: => FR[R1, E2, A1]): FR[R1, E2, A1] = F.orElse(r, r2)
    @inline final def leftMap2[R1 <: R, E2, A1 >: A, E3](r2: => FR[R1, E2, A1])(f: (E, E2) => E3): FR[R1, E3, A1] = F.leftMap2(r, r2)(f)

    @inline final def widenError[E1 >: E]: FR[R, E1, A] = r
    @inline final def widenBoth[E1 >: E, A1](implicit @unused ev2: A <:< A1): FR[R, E1, A1] = r.asInstanceOf[FR[R, E1, A1]]
  }

  class BIOError3Ops[FR[-_, +_, +_], -R, +E, +A](override protected[this] val r: FR[R, E, A])(implicit override protected[this] val F: Error3[FR])
    extends BIOApplicativeError3Ops(r) {
    @inline final def flatMap[R1 <: R, E1 >: E, B](f0: A => FR[R1, E1, B]): FR[R1, E1, B] = F.flatMap[R1, E1, A, B](r)(f0)
    @inline final def tap[R1 <: R, E1 >: E, B](f0: A => FR[R1, E1, Unit]): FR[R1, E1, A] = F.flatMap[R1, E1, A, A](r)(a => F.map(f0(a))(_ => a))

    @inline final def flatten[R1 <: R, E1 >: E, A1](implicit ev: A <:< FR[R1, E1, A1]): FR[R1, E1, A1] = F.flatten(F.widen(r))

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
    @inline final def fromOption[R1 <: R, E1 >: E, A1](errorOnNone: => E1)(implicit ev1: A <:< Option[A1]): FR[R1, E1, A1] =
      F.flatMap[R1, E1, A, A1](r)(F.fromOption(errorOnNone)(_))

    @inline final def retryUntil(f: E => Boolean): FR[R, E, A] =
      F.retryUntil(r)(f)

    @inline final def retryUntilM[R1 <: R](f: E => FR[R1, Nothing, Boolean]): FR[R1, E, A] =
      F.retryUntilM[R1, E, A](r)(f)

    @inline final def retryWhile(f: E => Boolean): FR[R, E, A] =
      F.retryWhile(r)(f)

    @inline final def retryWhileM[R1 <: R](f: E => FR[R1, Nothing, Boolean]): FR[R1, E, A] =
      F.retryWhileM[R1, E, A](r)(f)

    @inline final def someOrElse[B](default: => B)(implicit ev: A <:< Option[B]): FR[R, E, B] =
      F.someOrElse[R, E, B](r.widen)(default)

    @inline final def someOrElseM[R1 <: R, E1 >: E, B](default: FR[R1, E1, B])(implicit ev: A <:< Option[B]): FR[R1, E1, B] =
      F.someOrElseM[R1, E1, B](r.widen)(default)

    @inline final def someOrFail[B, E1 >: E](e: => E1)(implicit ev: A <:< Option[B]): FR[R, E1, B] =
      F.someOrFail[R, E1, B](r.widen)(e)

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

  class BIOBracket3Ops[FR[-_, +_, +_], -R, +E, +A](override protected[this] val r: FR[R, E, A])(implicit override protected[this] val F: Bracket3[FR])
    extends BIOError3Ops(r) {
    @inline final def bracket[R1 <: R, E1 >: E, B](release: A => FR[R1, Nothing, Unit])(use: A => FR[R1, E1, B]): FR[R1, E1, B] =
      F.bracket(r: FR[R1, E1, A])(release)(use)
    @inline final def bracketCase[R1 <: R, E1 >: E, B](release: (A, Exit[E1, B]) => FR[R1, Nothing, Unit])(use: A => FR[R1, E1, B]): FR[R1, E1, B] =
      F.bracketCase(r: FR[R1, E1, A])(release)(use)
    @inline final def guaranteeCase[R1 <: R](cleanup: Exit[E, A] => FR[R1, Nothing, Unit]): FR[R1, E, A] = F.guaranteeCase(r, cleanup)
  }

  class BIOPanic3Ops[FR[-_, +_, +_], -R, +E, +A](override protected[this] val r: FR[R, E, A])(implicit override protected[this] val F: Panic3[FR])
    extends BIOBracket3Ops(r) {
    @inline final def sandbox: FR[R, Exit.Failure[E], A] = F.sandbox(r)
    @inline final def sandboxExit: FR[R, Nothing, Exit[E, A]] = F.redeemPure(F.sandbox(r))(identity, Exit.Success(_))
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
      *     .catchAll(_ => BIO(println("Caught error!")))
      * }}}
      */
    @inline final def sandboxToThrowable(implicit ev: E <:< Throwable): FR[R, Throwable, A] =
      F.leftMap(F.sandbox(r))(_.toThrowable)

    /** Convert Throwable typed error into a defect */
    @inline final def orTerminate(implicit ev: E <:< Throwable): FR[R, Nothing, A] = F.catchAll(r)(F.terminate(_))
  }

  class BIO3Ops[FR[-_, +_, +_], -R, +E, +A](override protected[this] val r: FR[R, E, A])(implicit override protected[this] val F: IO3[FR]) extends BIOPanic3Ops(r) {
    @inline final def bracketAuto[R1 <: R, E1 >: E, B](use: A => FR[R1, E1, B])(implicit ev: A <:< AutoCloseable): FR[R1, E1, B] =
      F.bracket(r: FR[R1, E1, A])(c => F.sync(c.close()))(use)
  }

  class BIOParallel3Ops[FR[-_, +_, +_], -R, +E, +A](protected[this] val r: FR[R, E, A])(implicit protected[this] val F: Parallel3[FR]) {
    @inline final def zipWithPar[R1 <: R, E1 >: E, B, C](that: FR[R1, E1, B])(f: (A, B) => C): FR[R1, E1, C] = F.zipWithPar(r, that)(f)
    @inline final def zipPar[R1 <: R, E1 >: E, B](that: FR[R1, E1, B]): FR[R1, E1, (A, B)] = F.zipPar(r, that)
    @inline final def zipParLeft[R1 <: R, E1 >: E, B](that: FR[R1, E1, B]): FR[R1, E1, A] = F.zipParLeft(r, that)
    @inline final def zipParRight[R1 <: R, E1 >: E, B](that: FR[R1, E1, B]): FR[R1, E1, B] = F.zipParRight(r, that)
  }

  class BIOConcurrent3Ops[FR[-_, +_, +_], -R, +E, +A](override protected[this] val r: FR[R, E, A])(implicit override protected[this] val F: Concurrent3[FR])
    extends BIOParallel3Ops(r) {
    @inline final def race[R1 <: R, E1 >: E, A1 >: A](that: FR[R1, E1, A1]): FR[R1, E1, A1] = F.race(r, that)
    @inline final def racePair[R1 <: R, E1 >: E, A1 >: A](that: FR[R1, E1, A1]): FR[R1, E1, Either[(A, Fiber3[FR, E1, A1]), (Fiber3[FR, E1, A], A1)]] =
      F.racePair(r, that)
    @inline final def uninterruptible: FR[R, E, A] = F.uninterruptible(r)
  }

  class BIOAsync3Ops[FR[-_, +_, +_], -R, +E, +A](override protected[this] val r: FR[R, E, A])(implicit override protected[this] val F: Async3[FR]) extends BIO3Ops(r) {
    @inline final def zipWithPar[R1 <: R, E1 >: E, B, C](that: FR[R1, E1, B])(f: (A, B) => C): FR[R1, E1, C] = F.zipWithPar(r, that)(f)
    @inline final def zipPar[R1 <: R, E1 >: E, B](that: FR[R1, E1, B]): FR[R1, E1, (A, B)] = F.zipPar(r, that)
    @inline final def zipParLeft[R1 <: R, E1 >: E, B](that: FR[R1, E1, B]): FR[R1, E1, A] = F.zipParLeft(r, that)
    @inline final def zipParRight[R1 <: R, E1 >: E, B](that: FR[R1, E1, B]): FR[R1, E1, B] = F.zipParRight(r, that)

    @inline final def race[R1 <: R, E1 >: E, A1 >: A](that: FR[R1, E1, A1]): FR[R1, E1, A1] = F.race(r, that)
    @inline final def racePair[R1 <: R, E1 >: E, A1 >: A](that: FR[R1, E1, A1]): FR[R1, E1, Either[(A, Fiber3[FR, E1, A1]), (Fiber3[FR, E1, A], A1)]] =
      F.racePair(r, that)
    @inline final def uninterruptible: FR[R, E, A] = F.uninterruptible(r)
  }

  final class BIOTemporal3Ops[FR[-_, +_, +_], -R, +E, +A](protected[this] val r: FR[R, E, A])(implicit protected[this] val F: Temporal3[FR]) {
    @inline final def retryOrElse[R1 <: R, A2 >: A, E2](duration: FiniteDuration, orElse: => FR[R1, E2, A2]): FR[R1, E2, A2] =
      F.retryOrElse[R1, E, A2, E2](r)(duration, orElse)
    @inline final def repeatUntil[E1 >: E, A2](tooManyAttemptsError: => E1, sleep: FiniteDuration, maxAttempts: Int)(implicit ev: A <:< Option[A2]): FR[R, E1, A2] =
      F.repeatUntil[R, E1, A2](new BIOFunctor3Ops(r)(F.InnerF).widen)(tooManyAttemptsError, sleep, maxAttempts)

    @inline final def timeout(duration: Duration): FR[R, E, Option[A]] = F.timeout(duration)(r)
    @inline final def timeoutFail[E1 >: E](e: E1)(duration: Duration): FR[R, E1, A] = F.timeoutFail(duration)(e, r)
  }

  final class BIOFork3Ops[FR[-_, +_, +_], -R, +E, +A](private val r: FR[R, E, A])(implicit private val F: Fork3[FR]) {
    @inline final def fork: FR[R, Nothing, Fiber3[FR, E, A]] = F.fork(r)
  }

  class BIOProfunctorOps[+FR[-_, +_, +_], -R, +E, +A](protected[this] val r: FR[R, E, A])(implicit protected[this] val F: Profunctor3[FR]) {
    @inline final def dimap[R1, A1](f: R1 => R)(g: A => A1): FR[R1, E, A1] = F.dimap(r)(f)(g)
  }

  class BIOArrowOps[FR[-_, +_, +_], -R, +E, +A](override protected[this] val r: FR[R, E, A])(implicit override protected[this] val F: Arrow3[FR])
    extends BIOProfunctorOps(r) {
    @inline final def andThen[E1 >: E, A1](g: FR[A, E1, A1]): FR[R, E1, A1] = F.andThen(r, g)
    @inline final def compose[E1 >: E, R1](g: FR[R1, E1, R]): FR[R1, E1, A] = F.andThen(g, r)
  }

  class BIOArrowChoiceOps[FR[-_, +_, +_], -R, +E, +A](override protected[this] val r: FR[R, E, A])(implicit override protected[this] val F: ArrowChoice3[FR])
    extends BIOArrowOps(r) {
    @inline final def choice[R1 <: R, E1 >: E, A1 >: A, R2](g: FR[R2, E1, A1]): FR[Either[R1, R2], E1, A1] = F.choice(r, g)
    @inline final def choose[R1 <: R, E1 >: E, R2, A1](g: FR[R2, E1, A1]): FR[Either[R1, R2], E1, Either[A, A1]] = F.choose(r, g)
  }

  final class BIOLocalOps[FR[-_, +_, +_], -R, +E, +A](protected[this] override val r: FR[R, E, A])(implicit override protected[this] val F: Local3[FR])
    extends BIOArrowChoiceOps(r) {
    @inline final def provide(env: => R): FR[Any, E, A] = F.provide(r)(env)
    @inline final def contramap[R0 <: R](f: R0 => R): FR[R0, E, A] = F.contramap(r)(f)
  }

  final class BIOLocalOpsKleisliSyntax[FR[-_, +_, +_], R, E, A](private val r: FR[R, E, A])(implicit private val F: Local3[FR]) {
    @inline final def toKleisli: Kleisli[FR[Any, E, ?], R, A] = F.toKleisli(r)
  }

  trait BIO3ImplicitPuns extends BIO3ImplicitPuns1 {
    @inline implicit final def BIOTemporal3[FR[-_, +_, +_]: Temporal3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOTemporal3Ops[FR, R, E, A] =
      new BIO3Syntax.BIOTemporal3Ops[FR, R, E, A](self)
    @inline implicit final def BIOTemporal3[FR[-_, +_, +_]: Error3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOError3Ops[FR, R, E, A] =
      new BIO3Syntax.BIOError3Ops[FR, R, E, A](self)
    @inline final def BIOTemporal3[FR[-_, +_, +_]: Temporal3]: Temporal3[FR] = implicitly

    @inline implicit final def BIOFork3[FR[-_, +_, +_]: Fork3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOFork3Ops[FR, R, E, A] =
      new BIO3Syntax.BIOFork3Ops[FR, R, E, A](self)
    @inline final def BIOFork3[FR[-_, +_, +_]: Fork3]: Fork3[FR] = implicitly
  }
  trait BIO3ImplicitPuns1 extends BIO3ImplicitPuns2 {
    @inline implicit final def BIOAsync3[FR[-_, +_, +_]: Async3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOAsync3Ops[FR, R, E, A] =
      new BIO3Syntax.BIOAsync3Ops[FR, R, E, A](self)
    @inline final def BIOAsync3[FR[-_, +_, +_]: Async3]: Async3[FR] = implicitly
  }
  trait BIO3ImplicitPuns2 extends BIO3ImplicitPuns3 {
    @inline implicit final def BIOConcurrent3[FR[-_, +_, +_]: Concurrent3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOConcurrent3Ops[FR, R, E, A] =
      new BIO3Syntax.BIOConcurrent3Ops[FR, R, E, A](self)
    @inline implicit final def BIOConcurrent3[FR[-_, +_, +_]: Panic3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOPanic3Ops[FR, R, E, A] =
      new BIO3Syntax.BIOPanic3Ops[FR, R, E, A](self)
    @inline final def BIOConcurrent3[FR[-_, +_, +_]: Concurrent3]: Concurrent3[FR] = implicitly
  }
  trait BIO3ImplicitPuns3 extends BIO3ImplicitPuns4 {
    @inline implicit final def BIOParallel3[FR[-_, +_, +_]: Parallel3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOParallel3Ops[FR, R, E, A] =
      new BIO3Syntax.BIOParallel3Ops[FR, R, E, A](self)
    @inline implicit final def BIOParallel3[F[-_, +_, +_]: Monad3, R, E, A](self: F[R, E, A]): BIO3Syntax.BIOMonad3Ops[F, R, E, A] =
      new BIO3Syntax.BIOMonad3Ops[F, R, E, A](self)
    @inline final def BIOParallel3[FR[-_, +_, +_]: Parallel3]: Parallel3[FR] = implicitly
  }
  trait BIO3ImplicitPuns4 extends BIO3ImplicitPuns5 {
    @inline implicit final def BIO3[FR[-_, +_, +_]: IO3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIO3Ops[FR, R, E, A] = new BIO3Syntax.BIO3Ops[FR, R, E, A](self)
    /**
      * Shorthand for [[IO3#syncThrowable]]
      *
      * {{{
      *   IO3(println("Hello world!"))
      * }}}
      */
    @inline final def BIO3[FR[-_, +_, +_], A](effect: => A)(implicit F: IO3[FR]): FR[Any, Throwable, A] = F.syncThrowable(effect)
    @inline final def BIO3[FR[-_, +_, +_]: IO3]: IO3[FR] = implicitly
  }
  trait BIO3ImplicitPuns5 extends BIO3ImplicitPuns6 {
    @inline implicit final def BIOPanic3[FR[-_, +_, +_]: Panic3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOPanic3Ops[FR, R, E, A] =
      new BIO3Syntax.BIOPanic3Ops[FR, R, E, A](self)
    @inline final def BIOPanic3[FR[-_, +_, +_]: Panic3]: Panic3[FR] = implicitly
  }
  trait BIO3ImplicitPuns6 extends BIO3ImplicitPuns7 {
    @inline implicit final def BIOBracket3[FR[-_, +_, +_]: Bracket3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOBracket3Ops[FR, R, E, A] =
      new BIO3Syntax.BIOBracket3Ops[FR, R, E, A](self)
    @inline final def BIOBracket3[FR[-_, +_, +_]: Bracket3]: Bracket3[FR] = implicitly
  }
  trait BIO3ImplicitPuns7 extends BIO3ImplicitPuns8 {
    @inline implicit final def BIOError3[FR[-_, +_, +_]: Error3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOError3Ops[FR, R, E, A] =
      new BIO3Syntax.BIOError3Ops[FR, R, E, A](self)
    @inline final def BIOError3[FR[-_, +_, +_]: Error3]: Error3[FR] = implicitly
  }
  trait BIO3ImplicitPuns8 extends BIO3ImplicitPuns9 {
    @inline implicit final def BIOApplicativeError3[FR[-_, +_, +_]: ApplicativeError3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOApplicativeError3Ops[FR, R, E, A] =
      new BIO3Syntax.BIOApplicativeError3Ops[FR, R, E, A](self)
    @inline final def BIOApplicativeError3[FR[-_, +_, +_]: ApplicativeError3]: ApplicativeError3[FR] = implicitly
  }
  trait BIO3ImplicitPuns9 extends BIO3ImplicitPuns10 {
    @inline implicit final def BIOGuarantee3[FR[-_, +_, +_]: Guarantee3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOGuarantee3Ops[FR, R, E, A] =
      new BIO3Syntax.BIOGuarantee3Ops[FR, R, E, A](self)
    @inline final def BIOGuarantee3[FR[-_, +_, +_]: Guarantee3]: Guarantee3[FR] = implicitly
  }
  trait BIO3ImplicitPuns10 extends BIO3ImplicitPuns11 {
    @inline implicit final def BIOMonad3[FR[-_, +_, +_]: Monad3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOMonad3Ops[FR, R, E, A] =
      new BIO3Syntax.BIOMonad3Ops[FR, R, E, A](self)
    @inline final def BIOMonad3[FR[-_, +_, +_]: Monad3]: Monad3[FR] = implicitly
  }
  trait BIO3ImplicitPuns11 extends BIO3ImplicitPuns12 {
    @inline implicit final def BIOApplicative3[FR[-_, +_, +_]: Applicative3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOApplicative3Ops[FR, R, E, A] =
      new BIO3Syntax.BIOApplicative3Ops[FR, R, E, A](self)
    @inline final def BIOApplicative3[FR[-_, +_, +_]: Applicative3]: Applicative3[FR] = implicitly
  }
  trait BIO3ImplicitPuns12 extends BIO3ImplicitPuns13 {
    @inline implicit final def BIOBifunctor3[FR[-_, +_, +_]: Bifunctor3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOBifunctor3Ops[FR, R, E, A] =
      new BIO3Syntax.BIOBifunctor3Ops[FR, R, E, A](self)
    @inline implicit final def BIOBifunctor3[FR[-_, +_, +_]: Functor3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOFunctor3Ops[FR, R, E, A] =
      new BIO3Syntax.BIOFunctor3Ops[FR, R, E, A](self)
    @inline final def BIOBifunctor3[FR[-_, +_, +_]: Bifunctor3]: Bifunctor3[FR] = implicitly
  }
  trait BIO3ImplicitPuns13 extends BIOImplicitPuns14 {
    @inline implicit final def BIOFunctor3[FR[-_, +_, +_]: Functor3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOFunctor3Ops[FR, R, E, A] =
      new BIO3Syntax.BIOFunctor3Ops[FR, R, E, A](self)
    @inline final def BIOFunctor3[FR[-_, +_, +_]: Functor3]: Functor3[FR] = implicitly
  }
  trait BIOImplicitPuns14 extends BIOImplicitPuns15 {
    // Note, as long as these auxilary conversions to BIOMonad/Applicative/Functor syntaxes etc.
    // have the same output type as BIOMonad3/etc conversions above, they will avoid the specificity rule
    // and _will not_ clash (because the outputs are equal, not <:<).
    // If you merge them into `BIOLocalSyntax with BIOMonad3`, they _will_ start clashing

    @inline implicit final def BIOLocal[FR[-_, +_, +_]: Local3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOLocalOps[FR, R, E, A] =
      new BIO3Syntax.BIOLocalOps[FR, R, E, A](self)
    @inline implicit final def BIOLocal[FR[-_, +_, +_]: Monad3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOMonad3Ops[FR, R, E, A] =
      new BIO3Syntax.BIOMonad3Ops[FR, R, E, A](self)
    @inline implicit final def BIOLocal[FR[-_, +_, +_]: Local3, R, E, A](
      self: FR[R, E, A]
    )(implicit d1: DummyImplicit
    ): BIO3Syntax.BIOLocalOpsKleisliSyntax[FR, R, E, A] = new BIO3Syntax.BIOLocalOpsKleisliSyntax[FR, R, E, A](self)
    @inline final def BIOLocal[FR[-_, +_, +_]: Local3]: Local3[FR] = implicitly
  }
  trait BIOImplicitPuns15 extends BIOImplicitPuns16 {
    @inline implicit final def BIOMonadAsk[FR[-_, +_, +_]: Monad3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOMonad3Ops[FR, R, E, A] =
      new BIO3Syntax.BIOMonad3Ops[FR, R, E, A](self)
    @inline final def BIOMonadAsk[FR[-_, +_, +_]: Ask3]: Ask3[FR] = implicitly
  }
  trait BIOImplicitPuns16 extends BIOImplicitPuns17 {
    @inline implicit final def BIOAsk[FR[-_, +_, +_]: Applicative3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOApplicative3Ops[FR, R, E, A] =
      new BIO3Syntax.BIOApplicative3Ops[FR, R, E, A](self)
    @inline final def BIOAsk[FR[-_, +_, +_]: Ask3]: Ask3[FR] = implicitly
  }
  trait BIOImplicitPuns17 extends BIOImplicitPuns18 {
    @inline implicit final def BIOArrowChoice[FR[-_, +_, +_]: ArrowChoice3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOArrowChoiceOps[FR, R, E, A] =
      new BIO3Syntax.BIOArrowChoiceOps[FR, R, E, A](self)
    @inline implicit final def BIOArrowChoice[FR[-_, +_, +_]: Functor3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOFunctor3Ops[FR, R, E, A] =
      new BIO3Syntax.BIOFunctor3Ops[FR, R, E, A](self)
    @inline final def BIOArrowChoice[FR[-_, +_, +_]: ArrowChoice3]: ArrowChoice3[FR] = implicitly
  }
  trait BIOImplicitPuns18 extends BIOImplicitPuns19 {
    @inline implicit final def BIOArrow[FR[-_, +_, +_]: Arrow3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOArrowOps[FR, R, E, A] =
      new BIO3Syntax.BIOArrowOps[FR, R, E, A](self)
    @inline implicit final def BIOArrow[FR[-_, +_, +_]: Functor3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOFunctor3Ops[FR, R, E, A] =
      new BIO3Syntax.BIOFunctor3Ops[FR, R, E, A](self)
    @inline final def BIOArrow[FR[-_, +_, +_]: Arrow3]: Arrow3[FR] = implicitly
  }
  trait BIOImplicitPuns19 extends BIOImplicitPuns20 {
    @inline implicit final def BIOProfunctor[FR[-_, +_, +_]: Profunctor3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOProfunctorOps[FR, R, E, A] =
      new BIO3Syntax.BIOProfunctorOps[FR, R, E, A](self)
    @inline implicit final def BIOProfunctor[FR[-_, +_, +_]: Functor3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOFunctor3Ops[FR, R, E, A] =
      new BIO3Syntax.BIOFunctor3Ops[FR, R, E, A](self)
    @inline final def BIOProfunctor[FR[-_, +_, +_]: Profunctor3]: Profunctor3[FR] = implicitly
  }
  trait BIOImplicitPuns20 {
    @deprecated("Use Error3", "0.11")
    @inline implicit final def BIOMonadError3[FR[-_, +_, +_]: Error3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOError3Ops[FR, R, E, A] =
      new BIO3Syntax.BIOError3Ops[FR, R, E, A](self)
    @deprecated("Use Error3", "0.11")
    @inline final def BIOMonadError3[FR[-_, +_, +_]: Error3]: Error3[FR] = implicitly
  }

}
