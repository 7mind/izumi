package izumi.functional.bio.syntax

import izumi.functional.bio.{Exit, WeakTemporal2, WithFilter}
import izumi.fundamentals.platform.language.SourceFilePositionMaterializer

import scala.annotation.{targetName, unused}
import scala.compiletime.summonInline

/**
  * "Shadow" extension methods keyed on a `WeakTemporal2[F]` given, routed
  * through its `InnerF: Error2[F]`. Mixed into
  * [[izumi.functional.bio.WeakTemporal2]] so that a `given WeakTemporal2[F]`
  * in scope carries the full [[Error2]]-and-below extension surface as
  * members — case 1 callers (`def x[F: WeakTemporal2] = a.flatMap(…)`) no
  * longer need `implicit val _E: Error2[F] = summon[WT[F]].InnerF`.
  *
  * Each extension's `F` is a *free* type parameter (not the enclosing trait's),
  * so the desugared methods have an extra HKT parameter relative to
  * [[izumi.functional.bio.Monad2]]'s same-named typeclass methods
  * (`iterateWhile[E, A]` etc.). The arity gap avoids Scala 3's override-conflict
  * check in classes that happen to extend both `Monad2` and `WeakTemporal2`
  * (e.g. `MiniBIOAsync`, `TemporalZio`). All shadows carry a distinct
  * `@targetName` for JVM-level disambiguation from the main
  * [[Monad2ExtensionMethods]] / [[Error2ExtensionMethods]] / … equivalents.
  *
  * Multiple sibling-typeclass givens in scope on the same `F` (e.g.
  * `F: Concurrent2: Temporal2`) still hit standard Scala 3 extension-method
  * ambiguity on shared-ancestor methods — the caller disambiguates by pinning
  * the desired parent typeclass (`implicit val _P: Panic2[F] =
  * summon[Concurrent2[F]].InnerF`). Scala 3 has no priority axis between
  * unrelated `given` instances analogous to Scala 2's implicit-conversion chain.
  */
trait InnerFExtensionMethodsFromWeakTemporal2 {
  extension [F[+_, +_], E, A](r: F[E, A])(using wt: WeakTemporal2[F]) {

    // ---- Functor2 level ----

    @targetName("mapInnerFExt")
    def map[B](f: A => B): F[E, B] = wt.InnerF.map(r)(f)

    @targetName("asInnerFExt")
    infix def as[B](b: => B): F[E, B] = wt.InnerF.map(r)(_ => b)

    @targetName("voidInnerFExt")
    def void: F[E, Unit] = wt.InnerF.void(r)

    @targetName("widenInnerFExt")
    def widen[A1](using @unused ev: A <:< A1): F[E, A1] = r.asInstanceOf[F[E, A1]]

    @targetName("fromOptionOrInnerFExt")
    def fromOptionOr[B, C](valueOnNone: => C)(using @unused ev: A <:< Option[B], ev2: C <:< B): F[E, B] =
      wt.InnerF.fromOptionOr(ev2(valueOnNone), r.asInstanceOf[F[E, Option[B]]])

    // ---- Applicative2 level ----

    @targetName("andThenInnerFExt")
    def *>[E1 >: E, B](f0: => F[E1, B]): F[E1, B] = wt.InnerF.*>(r.asInstanceOf[F[E1, A]], f0)

    @targetName("andKeepInnerFExt")
    def <*[E1 >: E, B](f0: => F[E1, B]): F[E1, A] = wt.InnerF.<*(r.asInstanceOf[F[E1, A]], f0)

    @targetName("zipInnerFExt")
    infix def zip[E2 >: E, B](r2: => F[E2, B]): F[E2, (A, B)] = wt.InnerF.zip(r.asInstanceOf[F[E2, A]], r2)

    @targetName("map2InnerFExt")
    def map2[E2 >: E, B, C](r2: => F[E2, B])(f: (A, B) => C): F[E2, C] = wt.InnerF.map2(r.asInstanceOf[F[E2, A]], r2)(f)

    @targetName("foreverInnerFExt")
    def forever: F[E, Nothing] = wt.InnerF.forever(r)

    // ---- Monad2 level ----

    @targetName("flatMapInnerFExt")
    def flatMap[E1 >: E, B](f0: A => F[E1, B]): F[E1, B] = wt.InnerF.flatMap[E1, A, B](r.asInstanceOf[F[E1, A]])(f0)

    @targetName("tapInnerFExt")
    def tap[E1 >: E](f0: A => F[E1, Unit]): F[E1, A] = wt.InnerF.tap(r.asInstanceOf[F[E1, A]], f0)

    @targetName("flattenInnerFExt")
    def flatten[E1 >: E, A1](using ev: A <:< F[E1, A1]): F[E1, A1] = wt.InnerF.flatten(r.asInstanceOf[F[E1, F[E1, A1]]])

    @targetName("iterateWhileInnerFExt")
    def iterateWhile(p: A => Boolean): F[E, A] = wt.InnerF.iterateWhile(r)(p)

    @targetName("iterateUntilInnerFExt")
    def iterateUntil(p: A => Boolean): F[E, A] = wt.InnerF.iterateUntil(r)(p)

    @targetName("fromOptionFInnerFExt")
    def fromOptionF[E1 >: E, B, C](fallbackOnNone: => F[E1, C])(using @unused ev: A <:< Option[B], ev2: C <:< B): F[E1, B] =
      wt.InnerF.fromOptionF[E1, B](fallbackOnNone.asInstanceOf[F[E1, B]], r.asInstanceOf[F[E1, Option[B]]])

    // ---- Bifunctor2 level (reached via Error2 <: AppError2 <: Bifunctor2) ----

    @targetName("leftMapInnerFExt")
    def leftMap[E2](f: E => E2): F[E2, A] = wt.InnerF.leftMap(r)(f)

    @targetName("bimapInnerFExt")
    def bimap[E2, B](f: E => E2, g: A => B): F[E2, B] = wt.InnerF.bimap(r)(f, g)

    @targetName("widenErrorInnerFExt")
    def widenError[E1](using @unused ev: E <:< E1): F[E1, A] = r.asInstanceOf[F[E1, A]]

    @targetName("widenBothInnerFExt")
    def widenBoth[E1, A1](using @unused ev1: E <:< E1, @unused ev2: A <:< A1): F[E1, A1] = r.asInstanceOf[F[E1, A1]]

    // ---- Guarantee2 level ----

    @targetName("guaranteeInnerFExt")
    def guarantee(cleanup: F[Nothing, Unit]): F[E, A] = wt.InnerF.guarantee(r, cleanup)

    // ---- ApplicativeError2 level ----

    @targetName("orElseInnerFExt")
    def orElse[E2, A1 >: A](r2: => F[E2, A1]): F[E2, A1] = wt.InnerF.orElse(r, r2)

    @targetName("leftMap2InnerFExt")
    def leftMap2[E2, A1 >: A, E3](r2: => F[E2, A1])(f: (E, E2) => E3): F[E3, A1] = wt.InnerF.leftMap2(r, r2)(f)

    // ---- Error2 level ----

    @targetName("catchAllInnerFExt")
    def catchAll[E2, A2 >: A](h: E => F[E2, A2]): F[E2, A2] = wt.InnerF.catchAll[E, A2, E2](r)(h)

    @targetName("catchSomeInnerFExt")
    def catchSome[E1 >: E, A2 >: A](h: PartialFunction[E, F[E1, A2]]): F[E1, A2] = wt.InnerF.catchSome[E, A2, E1](r)(h)

    @targetName("redeemInnerFExt")
    def redeem[E2, B](err: E => F[E2, B], succ: A => F[E2, B]): F[E2, B] = wt.InnerF.redeem[E, A, E2, B](r)(err, succ)

    @targetName("redeemPureInnerFExt")
    def redeemPure[B](err: E => B, succ: A => B): F[Nothing, B] = wt.InnerF.redeemPure(r)(err, succ)

    @targetName("attemptInnerFExt")
    def attempt: F[Nothing, Either[E, A]] = wt.InnerF.attempt(r)

    @targetName("tapErrorInnerFExt")
    def tapError[E1 >: E](f: E => F[E1, Unit]): F[E1, A] = wt.InnerF.tapError[E, A, E1](r)(f)

    @targetName("leftFlatMapInnerFExt")
    def leftFlatMap[E2](f: E => F[Nothing, E2]): F[E2, A] = wt.InnerF.leftFlatMap(r)(f)

    @targetName("flipInnerFExt")
    def flip: F[A, E] = wt.InnerF.flip(r)

    @targetName("tapBothInnerFExt")
    def tapBoth[E1 >: E, E2 >: E1](err: E => F[E1, Unit])(succ: A => F[E2, Unit]): F[E2, A] = wt.InnerF.tapBoth[E, A, E2](r)(err, succ)

    @targetName("fromEitherInnerFExt")
    def fromEither[E1 >: E, A1](using ev: A <:< Either[E1, A1]): F[E1, A1] =
      wt.InnerF.flatMap[E1, A, A1](r)(a => wt.InnerF.fromEither[E1, A1](ev(a)))

    @targetName("fromOptionInnerFExt")
    def fromOption[E1 >: E, A1](errorOnNone: => E1)(using ev1: A <:< Option[A1]): F[E1, A1] =
      wt.InnerF.fromOption(errorOnNone, r.asInstanceOf[F[E1, Option[A1]]])

    @targetName("retryWhileInnerFExt")
    def retryWhile(f: E => Boolean): F[E, A] = wt.InnerF.retryWhile(r)(f)

    @targetName("retryWhileFInnerFExt")
    def retryWhileF(f: E => F[Nothing, Boolean]): F[E, A] = wt.InnerF.retryWhileF(r)(f)

    @targetName("retryUntilInnerFExt")
    def retryUntil(f: E => Boolean): F[E, A] = wt.InnerF.retryUntil(r)(f)

    @targetName("retryUntilFInnerFExt")
    def retryUntilF(f: E => F[Nothing, Boolean]): F[E, A] = wt.InnerF.retryUntilF(r)(f)

    @targetName("withFilterInnerFExt")
    def withFilter[A1 >: A](predicate: A => Boolean)(using filter: WithFilter[E], pos: SourceFilePositionMaterializer): F[E, A] =
      wt.InnerF.withFilter[E, A](r)(predicate)
  }
}
