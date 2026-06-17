package izumi.functional.bio.syntax

import izumi.functional.bio.{Error2, WithFilter}
import izumi.fundamentals.platform.language.SourceFilePositionMaterializer

import scala.annotation.targetName

trait Error2ExtensionMethods {
  extension [F[+_, +_], E, A](r: F[E, A])(using F: Error2[F]) {
    @targetName("catchAllExt")
    def catchAll[E2, A2 >: A](h: E => F[E2, A2]): F[E2, A2] = F.catchAll[E, A2, E2](r)(h)

    @targetName("catchSomeExt")
    def catchSome[E1 >: E, A2 >: A](h: PartialFunction[E, F[E1, A2]]): F[E1, A2] = F.catchSome[E, A2, E1](r)(h)

    @targetName("redeemExt")
    def redeem[E2, B](err: E => F[E2, B], succ: A => F[E2, B]): F[E2, B] = F.redeem[E, A, E2, B](r)(err, succ)

    @targetName("redeemPureExt")
    def redeemPure[B](err: E => B, succ: A => B): F[Nothing, B] = F.redeemPure(r)(err, succ)

    @targetName("attemptExt")
    def attempt: F[Nothing, Either[E, A]] = F.attempt(r)

    @targetName("tapErrorExt")
    def tapError[E1 >: E](f: E => F[E1, Unit]): F[E1, A] = F.tapError[E, A, E1](r)(f)

    @targetName("leftFlatMapExt")
    def leftFlatMap[E2](f: E => F[Nothing, E2]): F[E2, A] = F.leftFlatMap(r)(f)

    @targetName("flipExt")
    def flip: F[A, E] = F.flip(r)

    @targetName("tapBothExt")
    def tapBoth[E1 >: E, E2 >: E1](err: E => F[E1, Unit])(succ: A => F[E2, Unit]): F[E2, A] = F.tapBoth[E, A, E2](r)(err, succ)

    @targetName("fromEitherErrorExt")
    def fromEither[E1 >: E, A1](using ev: A <:< Either[E1, A1]): F[E1, A1] =
      F.flatMap[E1, A, A1](r)(a => F.fromEither[E1, A1](ev(a)))

    @targetName("fromOptionErrorExt")
    def fromOption[E1 >: E, A1](errorOnNone: => E1)(using ev1: A <:< Option[A1]): F[E1, A1] =
      F.fromOption(errorOnNone, r.asInstanceOf[F[E1, Option[A1]]])

    @targetName("retryWhileExt")
    def retryWhile(f: E => Boolean): F[E, A] = F.retryWhile(r)(f)

    @targetName("retryWhileFExt")
    def retryWhileF(f: E => F[Nothing, Boolean]): F[E, A] = F.retryWhileF(r)(f)

    @targetName("retryUntilExt")
    def retryUntil(f: E => Boolean): F[E, A] = F.retryUntil(r)(f)

    @targetName("retryUntilFExt")
    def retryUntilF(f: E => F[Nothing, Boolean]): F[E, A] = F.retryUntilF(r)(f)

    /** for-comprehensions sugar:
      *
      * {{{
      *   for {
      *     (1, 2) <- F.pure((2, 1)).widenError[NoSuchElementException]
      *   } yield ()
      * }}}
      *
      * Use [[widenError]] for pattern matching with non-Throwable errors:
      *
      * {{{
      *   val f = for {
      *     (1, 2) <- F.pure((2, 1)).widenError[Option[Unit]]
      *   } yield ()
      *   // f: F[Option[Unit], Unit] = F.fail(Some(())
      * }}}
      *
      * Scala 3 implementation note: unlike the Scala 2 Ops-class variant in
      * [[Syntax2]], this extension fixes `E1 = E` (the receiver's declared
      * error type) rather than accepting a widened `E1 >: E`. Scala 3's
      * inference of a free `E1 >: E` combined with `using WithFilter[E1]`
      * produces a *union* LUB (e.g. `String | NoSuchElementException`) because
      * of union types + covariant `WithFilter[+E]`, silently changing the
      * receiver's declared error type. Forcing `E1 = E` keeps the error type
      * the receiver already committed to and resolves `WithFilter[E]` via the
      * ordinary priority chain. Callers that want a different error type
      * (e.g. `NoSuchElementException` when `E = Nothing`) must use
      * [[widenError]] explicitly on the receiver before the for-comprehension.
      */
    @targetName("withFilterExt")
    def withFilter[A1 >: A](predicate: A => Boolean)(using filter: WithFilter[E], pos: SourceFilePositionMaterializer): F[E, A] =
      F.withFilter[E, A](r)(predicate)
  }
}
