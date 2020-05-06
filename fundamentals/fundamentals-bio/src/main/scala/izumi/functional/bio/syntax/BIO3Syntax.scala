package izumi.functional.bio.syntax

import cats.data.Kleisli
import izumi.functional.bio.syntax.BIO3Syntax.BIO3ImplicitPuns
import izumi.functional.bio.{BIO3, BIOApplicative3, BIOArrow, BIOArrowChoice, BIOAsk, BIOAsync3, BIOBifunctor3, BIOBracket3, BIOError3, BIOExit, BIOFiber3, BIOFork3, BIOFunctor3, BIOGuarantee3, BIOLocal, BIOMonad3, BIOMonadError3, BIOPanic3, BIOProfunctor, BIOTemporal3, BIOParallel3}

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
  def F[FR[-_, +_, +_]](implicit FR: BIOFunctor3[FR]): FR.type = FR
}

object BIO3Syntax {

  class BIOFunctor3Ops[+FR[-_, +_, +_], -R, +E, +A](protected[this] val r: FR[R, E, A])(implicit protected[this] val F: BIOFunctor3[FR]) {
    @inline final def map[B](f: A => B): FR[R, E, B] = F.map(r)(f)

    @inline final def as[B](b: => B): FR[R, E, B] = F.map(r)(_ => b)
    @inline final def void: FR[R, E, Unit] = F.void(r)
    @inline final def widen[A1](implicit @deprecated("unused", "") ev: A <:< A1): FR[R, E, A1] = r.asInstanceOf[FR[R, E, A1]]
  }

  final class BIOBifunctor3Ops[+FR[-_, +_, +_], -R, +E, +A](protected[this] val r: FR[R, E, A])(implicit protected[this] val F: BIOBifunctor3[FR]) {
    @inline final def leftMap[E2](f: E => E2): FR[R, E2, A] = F.leftMap(r)(f)
    @inline final def bimap[E2, B](f: E => E2, g: A => B): FR[R, E2, B] = F.bimap(r)(f, g)

    @inline final def widenError[E1 >: E]: FR[R, E1, A] = r
    @inline final def widenBoth[E1 >: E, A1](implicit @deprecated("unused","") ev2: A <:< A1): FR[R, E1, A1] = r.asInstanceOf[FR[R, E1, A1]]
  }

  class BIOApplicative3Ops[FR[-_, +_, +_], -R, +E, +A](override protected[this] val r: FR[R, E, A])(implicit override protected[this] val F: BIOApplicative3[FR])
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

  class BIOGuarantee3Ops[FR[-_, +_, +_], -R, +E, +A](override protected[this] val r: FR[R, E, A])(implicit override protected[this] val F: BIOGuarantee3[FR])
    extends BIOApplicative3Ops(r) {
    @inline final def guarantee[R1 <: R](cleanup: FR[R1, Nothing, Unit]): FR[R1, E, A] = F.guarantee(r, cleanup)
  }

  final class BIOMonad3Ops[FR[-_, +_, +_], -R, +E, +A](override protected[this] val r: FR[R, E, A])(implicit override protected[this] val F: BIOMonad3[FR])
    extends BIOApplicative3Ops(r) {
    @inline final def flatMap[R1 <: R, E1 >: E, B](f0: A => FR[R1, E1, B]): FR[R1, E1, B] = F.flatMap[R, E, A, R1, E1, B](r)(f0)
    @inline final def tap[R1 <: R, E1 >: E, B](f0: A => FR[R1, E1, Unit]): FR[R1, E1, A] = F.flatMap[R, E, A, R1, E1, A](r)(a => F.map(f0(a))(_ => a))

    @inline final def flatten[R1 <: R, E1 >: E, A1](implicit ev: A <:< FR[R1, E1, A1]): FR[R1, E1, A1] = F.flatten(F.widen(r))
  }

  class BIOError3Ops[FR[-_, +_, +_], -R, +E, +A](override protected[this] val r: FR[R, E, A])(implicit override protected[this] val F: BIOError3[FR])
    extends BIOGuarantee3Ops(r) {
    @inline final def leftMap[E2](f: E => E2): FR[R, E2, A] = F.leftMap(r)(f)
    @inline final def bimap[E2, B](f: E => E2, g: A => B): FR[R, E2, B] = F.bimap(r)(f, g)

    @inline final def widenError[E1 >: E]: FR[R, E1, A] = r
    @inline final def widenBoth[E1 >: E, A1](implicit @deprecated("unused","") ev2: A <:< A1): FR[R, E1, A1] = r.asInstanceOf[FR[R, E1, A1]]

    @inline final def catchAll[R1 <: R, E2, A2 >: A](h: E => FR[R1, E2, A2]): FR[R1, E2, A2] = F.catchAll[R1, E, A, E2, A2](r)(h)
    @inline final def catchSome[R1 <: R, E2 >: E, A2 >: A](h: PartialFunction[E, FR[R1, E2, A2]]): FR[R1, E2, A2] = F.catchSome[R1, E, A, E2, A2](r)(h)

    @inline final def redeemPure[B](err: E => B, succ: A => B): FR[R, Nothing, B] = F.redeemPure(r)(err, succ)

    @inline final def tapError[R1 <: R, E1 >: E](f: E => FR[R1, E1, Unit]): FR[R1, E1, A] = F.tapError[R1, E, A, E1](r)(f)

    @inline final def attempt: FR[R, Nothing, Either[E, A]] = F.attempt(r)
  }

  class BIOMonadError3Ops[FR[-_, +_, +_], -R, +E, +A](override protected[this] val r: FR[R, E, A])(implicit override protected[this] val F: BIOMonadError3[FR])
    extends BIOError3Ops(r) {
    @inline final def flatMap[R1 <: R, E1 >: E, B](f0: A => FR[R1, E1, B]): FR[R1, E1, B] = F.flatMap[R, E, A, R1, E1, B](r)(f0)
    @inline final def tap[R1 <: R, E1 >: E, B](f0: A => FR[R1, E1, Unit]): FR[R1, E1, A] = F.flatMap[R, E, A, R1, E1, A](r)(a => F.map(f0(a))(_ => a))

    @inline final def flatten[R1 <: R, E1 >: E, A1](implicit ev: A <:< FR[R1, E1, A1]): FR[R1, E1, A1] = F.flatten(F.widen(r))

    @inline final def redeem[R1 <: R, E2, B](err: E => FR[R1, E2, B], succ: A => FR[R1, E2, B]): FR[R1, E2, B] = F.redeem[R1, E, A, E2, B](r)(err, succ)

    @inline final def leftFlatMap[R1 <: R, E2](f: E => FR[R1, Nothing, E2]): FR[R1, E2, A] = F.leftFlatMap[R1, E, A, E2](r)(f)
    @inline final def flip: FR[R, A, E] = F.flip(r)

    @inline final def tapBoth[R1 <: R, E1 >: E, E2 >: E1](err: E => FR[R1, E1, Unit])(succ: A => FR[R1, E2, Unit]): FR[R1, E2, A] = F.tapBoth[R1, E, A, E2](r)(err, succ)

    @inline final def fromEither[R1 <: R, E1 >: E, A1](implicit ev: A <:< Either[E1, A1]): FR[R1, E1, A1] = F.flatMap[R, E, A, R1, E1, A1](r)(F.fromEither[E1, A1](_))
    @inline final def fromOption[R1 <: R, E1 >: E, A1](errorOnNone: => E1)(implicit ev1: A <:< Option[A1]): FR[R1, E1, A1] =
      F.flatMap[R, E, A, R1, E1, A1](r)(F.fromOption(errorOnNone)(_))

    /** for-comprehensions sugar:
      *
      * {{{
      *   for {
      *    (1, 2) <- F.pure((2, 1))
      *   } yield ()
      * }}}
      */
    @inline final def withFilter[E1 >: E](predicate: A => Boolean)(implicit ev: NoSuchElementException <:< E1): FR[R, E1, A] = F.withFilter[R, E1, A](r)(predicate)
  }

  class BIOBracket3Ops[FR[-_, +_, +_], -R, +E, +A](override protected[this] val r: FR[R, E, A])(implicit override protected[this] val F: BIOBracket3[FR])
    extends BIOMonadError3Ops(r) {
    @inline final def bracket[R1 <: R, E1 >: E, B](release: A => FR[R1, Nothing, Unit])(use: A => FR[R1, E1, B]): FR[R1, E1, B] = F.bracket(r: FR[R1, E1, A])(release)(use)
    @inline final def bracketCase[R1 <: R, E1 >: E, B](release: (A, BIOExit[E1, B]) => FR[R1, Nothing, Unit])(use: A => FR[R1, E1, B]): FR[R1, E1, B] = F.bracketCase(r: FR[R1, E1, A])(release)(use)
    @inline final def guaranteeCase[R1 <: R](cleanup: BIOExit[E, A] => FR[R1, Nothing, Unit]): FR[R1, E, A] = F.guaranteeCase(r, cleanup)
  }

  class BIOPanic3Ops[FR[-_, +_, +_], -R, +E, +A](override protected[this] val r: FR[R, E, A])(implicit override protected[this] val F: BIOPanic3[FR])
    extends BIOBracket3Ops(r) {
    @inline final def sandbox: FR[R, BIOExit.Failure[E], A] = F.sandbox(r)
    @inline final def sandboxBIOExit: FR[R, Nothing, BIOExit[E, A]] = F.redeemPure(F.sandbox(r))(identity, BIOExit.Success(_))

    /**
      * Catch all _defects_ in this effect and convert them to Throwable
      * Example:
      *
      * {{{
      *   BIO[FR].pure(1)
      *     .map(_ => ???)
      *     .sandboxThrowable
      *     .catchAll(_ => BIO(println("Caught error!")))
      * }}}
      *
      */
    @inline final def sandboxToThrowable(implicit ev: E <:< Throwable): FR[R, Throwable, A] =
      F.leftMap(F.sandbox(r))(_.toThrowable)

    /** Convert Throwable typed error into a defect */
    @inline final def orTerminate(implicit ev: E <:< Throwable): FR[R, Nothing, A] = F.catchAll(r)(F.terminate(_))
  }

  class BIO3Ops[FR[-_, +_, +_], -R, +E, +A](override protected[this] val r: FR[R, E, A])(implicit override protected[this] val F: BIO3[FR]) extends BIOPanic3Ops(r) {
    @inline final def bracketAuto[R1 <: R, E1 >: E, B](use: A => FR[R1, E1, B])(implicit ev: A <:< AutoCloseable): FR[R1, E1, B] =
      F.bracket(r: FR[R1, E1, A])(c => F.sync(c.close()))(use)
  }

  class BIOParallel3Ops[FR[-_ ,+_ , +_], -R, +E, +A](protected[this] val r: FR[R, E, A])(implicit protected[this] val F: BIOParallel3[FR]) {
    @inline final def zipWithPar[R1 <: R, E1 >: E, B, C](that: FR[R1, E1, B])(f: (A, B) => C): FR[R1, E1, C] = F.zipWithPar(r, that)(f)
    @inline final def zipPar[R1 <: R, E1 >: E, B](that: FR[R1, E1, B]): FR[R1, E1, (A, B)] = F.zipPar(r, that)
    @inline final def zipParLeft[R1 <: R, E1 >: E, B](that: FR[R1, E1, B]): FR[R1, E1, A] = F.zipParLeft(r, that)
    @inline final def zipParRight[R1 <: R, E1 >: E, B](that: FR[R1, E1, B]): FR[R1, E1, B] = F.zipParRight(r, that)
  }

  class BIOAsync3Ops[FR[-_, +_, +_], -R, +E, +A](override protected[this] val r: FR[R, E, A])(implicit override protected[this] val F: BIOAsync3[FR]) extends BIO3Ops(r) {
    @inline final def race[R1 <: R, E1 >: E, A1 >: A](that: FR[R1, E1, A1]): FR[R1, E1, A1] = F.race(r, that)
  }

  final class BIOTemporal3Ops[FR[-_, +_, +_], -R, +E, +A](override protected[this] val r: FR[R, E, A])(implicit override protected[this] val F: BIOTemporal3[FR])
    extends BIOAsync3Ops(r) {
    @inline final def retryOrElse[R1 <: R, A2 >: A, E2](duration: FiniteDuration, orElse: => FR[R1, E2, A2]): FR[R1, E2, A2] = F.retryOrElse[R1, A, E, A2, E2](r)(duration, orElse)
    @inline final def repeatUntil[E2 >: E, A2](onTimeout: => E2, sleep: FiniteDuration, maxAttempts: Int)(implicit ev: A <:< Option[A2]): FR[R, E2, A2] =
      F.repeatUntil[R, E2, A2](new BIOFunctor3Ops(r)(F).widen)(onTimeout, sleep, maxAttempts)

    @inline final def timeout(duration: Duration): FR[R, E, Option[A]] = F.timeout(r)(duration)
    @inline final def timeoutFail[E1 >: E](e: E1)(duration: Duration): FR[R, E1, A] =
      F.flatMap(timeout(duration): FR[R, E1, Option[A]])(_.fold[FR[R, E1, A]](F.fail(e))(F.pure))
  }

  final class BIOFork3Ops[FR[-_, +_, +_], -R, +E, +A](private val r: FR[R, E, A])(implicit private val F: BIOFork3[FR]) {
    @inline final def fork: FR[R, Nothing, BIOFiber3[FR, E, A]] = F.fork(r)
  }

  class BIOProfunctorOps[+FR[-_, +_, +_], -R, +E, +A](protected[this] val r: FR[R, E, A])(implicit protected[this] val F: BIOProfunctor[FR]) {
    @inline final def dimap[R1, A1](f: R1 => R)(g: A => A1): FR[R1, E, A1] = F.dimap(r)(f)(g)
  }

  class BIOArrowOps[FR[-_, +_, +_], -R, +E, +A](override protected[this] val r: FR[R, E, A])(implicit override protected[this] val F: BIOArrow[FR]) extends BIOProfunctorOps(r) {
    @inline final def andThen[E1 >: E, A1](g: FR[A, E1, A1]): FR[R, E1, A1] = F.andThen(r, g)
    @inline final def compose[E1 >: E, R1](g: FR[R1, E1, R]): FR[R1, E1, A] = F.andThen(g, r)
  }

  class BIOArrowChoiceOps[FR[-_, +_, +_], -R, +E, +A](override protected[this] val r: FR[R, E, A])(implicit override protected[this] val F: BIOArrowChoice[FR]) extends BIOArrowOps(r) {
    @inline final def choice[R1 <: R, E1 >: E, A1 >: A, R2](g: FR[R2, E1, A1]): FR[Either[R1, R2], E1, A1] = F.choice(r, g)
    @inline final def choose[R1 <: R, E1 >: E, R2, A1](g: FR[R2, E1, A1]): FR[Either[R1, R2], E1, Either[A, A1]] = F.choose(r, g)
  }

  final class BIOLocalOps[FR[-_, +_, +_], -R, +E, +A](protected[this] override val r: FR[R, E, A])(implicit override protected[this] val F: BIOLocal[FR]) extends BIOArrowChoiceOps(r) {
    @inline final def provide(env: => R): FR[Any, E, A] = F.provide(r)(env)
    @inline final def contramap[R0 <: R](f: R0 => R): FR[R0, E, A] = F.contramap(r)(f)
  }

  final class BIOLocalOpsKleisliSyntax[FR[-_, +_, +_], R, E, A](private val r: FR[R, E, A])(implicit private val F: BIOLocal[FR]) {
    @inline final def toKleisli: Kleisli[FR[Any, E, ?], R, A] = F.toKleisli(r)
  }

  trait BIO3ImplicitPuns extends BIO3ImplicitPuns1 {
    @inline implicit final def BIOTemporal3[FR[-_, +_, +_]: BIOTemporal3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOTemporal3Ops[FR, R, E, A] = new BIO3Syntax.BIOTemporal3Ops[FR, R, E, A](self)
    @inline final def BIOTemporal3[FR[-_, +_, +_]: BIOTemporal3]: BIOTemporal3[FR] = implicitly

    @inline implicit final def BIOFork3[FR[-_, +_, +_]: BIOFork3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOFork3Ops[FR, R, E, A] = new BIO3Syntax.BIOFork3Ops[FR, R, E, A](self)
    @inline final def BIOFork3[FR[-_, +_, +_]: BIOFork3]: BIOFork3[FR] = implicitly
  }
  trait BIO3ImplicitPuns1 extends BIO3ImplicitPuns2 {
    @inline implicit final def BIOAsync3[FR[-_, +_, +_]: BIOAsync3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOAsync3Ops[FR, R, E, A] = new BIO3Syntax.BIOAsync3Ops[FR, R, E, A](self)
    @inline final def BIOAsync3[FR[-_, +_, +_]: BIOAsync3]: BIOAsync3[FR] = implicitly
  }
  trait BIO3ImplicitPuns2 extends BIO3ImplicitPuns3 {
    @inline implicit final def BIOParallel3[FR[-_, +_, +_]: BIOParallel3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOParallel3Ops[FR, R, E, A] = new BIO3Syntax.BIOParallel3Ops[FR, R, E, A](self)
    @inline final def BIOParallel3[FR[-_, +_, +_]: BIOParallel3]: BIOParallel3[FR] = implicitly
  }
  trait BIO3ImplicitPuns3 extends BIO3ImplicitPuns4 {
    @inline implicit final def BIO3[FR[-_, +_, +_]: BIO3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIO3Ops[FR, R, E, A] = new BIO3Syntax.BIO3Ops[FR, R, E, A](self)
    /**
      * Shorthand for [[BIO3#syncThrowable]]
      *
      * {{{
      *   BIO(println("Hello world!"))
      * }}}
      */
    @inline final def BIO3[FR[-_, +_, +_], A](effect: => A)(implicit F: BIO3[FR]): FR[Any, Throwable, A] = F.syncThrowable(effect)
    @inline final def BIO3[FR[-_, +_, +_]: BIO3]: BIO3[FR] = implicitly
  }
  trait BIO3ImplicitPuns4 extends BIO3ImplicitPuns5 {
    @inline implicit final def BIOPanic3[FR[-_, +_, +_]: BIOPanic3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOPanic3Ops[FR, R, E, A] = new BIO3Syntax.BIOPanic3Ops[FR, R, E, A](self)
    @inline final def BIOPanic3[FR[-_, +_, +_]: BIOPanic3]: BIOPanic3[FR] = implicitly
  }
  trait BIO3ImplicitPuns5 extends BIO3ImplicitPuns6 {
    @inline implicit final def BIOBracket3[FR[-_, +_, +_]: BIOBracket3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOBracket3Ops[FR, R, E, A] = new BIO3Syntax.BIOBracket3Ops[FR, R, E, A](self)
    @inline final def BIOBracket3[FR[-_, +_, +_]: BIOBracket3]: BIOBracket3[FR] = implicitly
  }
  trait BIO3ImplicitPuns6 extends BIO3ImplicitPuns7 {
    @inline implicit final def BIOMonadError3[FR[-_, +_, +_]: BIOMonadError3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOMonadError3Ops[FR, R, E, A] = new BIO3Syntax.BIOMonadError3Ops[FR, R, E, A](self)
    @inline final def BIOMonadError3[FR[-_, +_, +_]: BIOMonadError3]: BIOMonadError3[FR] = implicitly
  }
  trait BIO3ImplicitPuns7 extends BIO3ImplicitPuns8 {
    @inline implicit final def BIOError3[FR[-_, +_, +_]: BIOError3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOError3Ops[FR, R, E, A] = new BIO3Syntax.BIOError3Ops[FR, R, E, A](self)
    @inline final def BIOError3[FR[-_, +_, +_]: BIOError3]: BIOError3[FR] = implicitly
  }
  trait BIO3ImplicitPuns8 extends BIO3ImplicitPuns9 {
    @inline implicit final def BIOGuarantee3[FR[-_, +_, +_]: BIOGuarantee3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOGuarantee3Ops[FR, R, E, A] = new BIO3Syntax.BIOGuarantee3Ops[FR, R, E, A](self)
    @inline final def BIOGuarantee3[FR[-_, +_, +_]: BIOGuarantee3]: BIOGuarantee3[FR] = implicitly
  }
  trait BIO3ImplicitPuns9 extends BIO3ImplicitPuns10 {
    @inline implicit final def BIOMonad3[FR[-_, +_, +_]: BIOMonad3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOMonad3Ops[FR, R, E, A] = new BIO3Syntax.BIOMonad3Ops[FR, R, E, A](self)
    @inline final def BIOMonad3[FR[-_, +_, +_]: BIOMonad3]: BIOMonad3[FR] = implicitly
  }
  trait BIO3ImplicitPuns10 extends BIO3ImplicitPuns11 {
    @inline implicit final def BIOApplicative3[FR[-_, +_, +_]: BIOApplicative3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOApplicative3Ops[FR, R, E, A] = new BIO3Syntax.BIOApplicative3Ops[FR, R, E, A](self)
    @inline final def BIOApplicative3[FR[-_, +_, +_]: BIOApplicative3]: BIOApplicative3[FR] = implicitly
  }
  trait BIO3ImplicitPuns11 extends BIO3ImplicitPuns12 {
    @inline implicit final def BIOBifunctor3[FR[-_, +_, +_]: BIOBifunctor3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOBifunctor3Ops[FR, R, E, A] = new BIO3Syntax.BIOBifunctor3Ops[FR, R, E, A](self)
    @inline implicit final def BIOBifunctor3[FR[-_, +_, +_]: BIOFunctor3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOFunctor3Ops[FR, R, E, A] = new BIO3Syntax.BIOFunctor3Ops[FR, R, E, A](self)
    @inline final def BIOBifunctor3[FR[-_, +_, +_]: BIOBifunctor3]: BIOBifunctor3[FR] = implicitly
  }
  trait BIO3ImplicitPuns12 extends BIOImplicitPuns13 {
    @inline implicit final def BIOFunctor3[FR[-_, +_, +_]: BIOFunctor3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOFunctor3Ops[FR, R, E, A] = new BIO3Syntax.BIOFunctor3Ops[FR, R, E, A](self)
    @inline final def BIOFunctor3[FR[-_, +_, +_]: BIOFunctor3]: BIOFunctor3[FR] = implicitly
  }
  trait BIOImplicitPuns13 extends BIOImplicitPuns14 {
    // Note, as long as these auxilary conversions to BIOMonad/Applicative/Functor syntaxes etc.
    // have the same output type as BIOMonad3/etc conversions above, they will avoid the specificity rule
    // and _will not_ clash (because the outputs are equal, not <:<).
    // If you merge them into `BIOLocalSyntax with BIOMonad3`, they _will_ start clashing

    @inline implicit final def BIOLocal[FR[-_, +_, +_]: BIOLocal, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOLocalOps[FR, R, E, A] = new BIO3Syntax.BIOLocalOps[FR, R, E, A](self)
    @inline implicit final def BIOLocal[FR[-_, +_, +_]: BIOMonad3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOMonad3Ops[FR, R, E, A] = new BIO3Syntax.BIOMonad3Ops[FR, R, E, A](self)
    @inline implicit final def BIOLocal[FR[-_, +_, +_]: BIOLocal, R, E, A](self: FR[R, E, A])(implicit d1: DummyImplicit): BIO3Syntax.BIOLocalOpsKleisliSyntax[FR, R, E, A] = new BIO3Syntax.BIOLocalOpsKleisliSyntax[FR, R, E, A](self)
    @inline final def BIOLocal[FR[-_, +_, +_]: BIOLocal]: BIOLocal[FR] = implicitly
  }
  trait BIOImplicitPuns14 extends BIOImplicitPuns15 {
    @inline implicit final def BIOMonadAsk[FR[-_, +_, +_]: BIOMonad3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOMonad3Ops[FR, R, E, A] = new BIO3Syntax.BIOMonad3Ops[FR, R, E, A](self)
    @inline final def BIOMonadAsk[FR[-_, +_, +_]: BIOAsk]: BIOAsk[FR] = implicitly
  }
  trait BIOImplicitPuns15 extends BIOImplicitPuns16 {
    @inline implicit final def BIOAsk[FR[-_, +_, +_]: BIOApplicative3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOApplicative3Ops[FR, R, E, A] = new BIO3Syntax.BIOApplicative3Ops[FR, R, E, A](self)
    @inline final def BIOAsk[FR[-_, +_, +_]: BIOAsk]: BIOAsk[FR] = implicitly
  }
  trait BIOImplicitPuns16 extends BIOImplicitPuns17 {
    @inline implicit final def BIOArrowChoice[FR[-_, +_, +_]: BIOArrowChoice, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOArrowChoiceOps[FR, R, E, A] = new BIO3Syntax.BIOArrowChoiceOps[FR, R, E, A](self)
    @inline implicit final def BIOArrowChoice[FR[-_, +_, +_]: BIOFunctor3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOFunctor3Ops[FR, R, E, A] = new BIO3Syntax.BIOFunctor3Ops[FR, R, E, A](self)
    @inline final def BIOArrowChoice[FR[-_, +_, +_]: BIOArrowChoice]: BIOArrowChoice[FR] = implicitly
  }
  trait BIOImplicitPuns17 extends BIOImplicitPuns18 {
    @inline implicit final def BIOArrow[FR[-_, +_, +_]: BIOArrow, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOArrowOps[FR, R, E, A] = new BIO3Syntax.BIOArrowOps[FR, R, E, A](self)
    @inline implicit final def BIOArrow[FR[-_, +_, +_]: BIOFunctor3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOFunctor3Ops[FR, R, E, A] = new BIO3Syntax.BIOFunctor3Ops[FR, R, E, A](self)
    @inline final def BIOArrow[FR[-_, +_, +_]: BIOArrow]: BIOArrow[FR] = implicitly
  }
  trait BIOImplicitPuns18 {
    @inline implicit final def BIOProfunctor[FR[-_, +_, +_]: BIOProfunctor, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOProfunctorOps[FR, R, E, A] = new BIO3Syntax.BIOProfunctorOps[FR, R, E, A](self)
    @inline implicit final def BIOProfunctor[FR[-_, +_, +_]: BIOFunctor3, R, E, A](self: FR[R, E, A]): BIO3Syntax.BIOFunctor3Ops[FR, R, E, A] = new BIO3Syntax.BIOFunctor3Ops[FR, R, E, A](self)
    @inline final def BIOProfunctor[FR[-_, +_, +_]: BIOProfunctor]: BIOProfunctor[FR] = implicitly
  }

}
