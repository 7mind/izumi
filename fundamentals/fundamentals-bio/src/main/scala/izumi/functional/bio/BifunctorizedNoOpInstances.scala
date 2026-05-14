package izumi.functional.bio

import izumi.functional.bio.PredefinedHelper.Predefined
import izumi.functional.bio.impl.MiniBIO

/** High-priority no-op identity instances for `Bifunctorized.NoOp[F, +_, +_]` when `F` is
  * already a bifunctor with a BIO `IO2` instance. The "no-op" is a type-level reinterpretation:
  * `Bifunctorized.NoOp[F, E, A]` is `F[E, A]` at runtime (cast via `asInstanceOf`), so the
  * existing `IO2[F]` instance can be cast directly to `IO2[Bifunctorized.NoOp[F, +_, +_]]`.
  *
  * Outranks PR-04's `CatsToBIOConversions.AsyncToBIO` (`NotPredefined.Of`) because this trait's
  * factory returns `Predefined.Of`. Mixed into `object Bifunctorized` so the implicit is in the
  * implicit scope of `Bifunctorized.NoOp[F, ?, ?]` typeclass searches — this ensures the no-op
  * is auto-available for `IO2[NoOp[F, ?, ?]]` lookups without polluting general
  * `Functor2[X]` / `IO2[X]` searches with an unbound `X`.
  *
  * Also provides the Identity special-case [[identityBifunctorizedHasIO2]] (Goal 3) — see its
  * scaladoc for the load-bearing rationale.
  */
trait BifunctorizedNoOpInstances {

  /** High-priority no-op identity instance for any bifunctor `F[+_, +_]` that already carries
    * a BIO `IO2` instance. Casts the existing `IO2[F]` dictionary to `IO2[NoOp[F, +_, +_]]` —
    * sound because `NoOp[F, E, A]` is an abstract type erased to `Object` at the JVM, identical
    * in representation to `F[E, A]`. The cast does not allocate.
    *
    * Outranks PR-04's `CatsToBIOConversions.AsyncToBIO` (`NotPredefined.Of`) when both apply
    * because this factory returns `Predefined.Of`.
    */
  @inline implicit final def bifunctorIsAlreadyBifunctor[F[+_, +_]](
    implicit F: IO2[F]
  ): Predefined.Of[IO2[Bifunctorized.NoOp[F, +_, +_]]] =
    Predefined(F.asInstanceOf[IO2[Bifunctorized.NoOp[F, +_, +_]]])

  /** Identity special-case (Goal 3): an [[IO2]] instance for [[Bifunctorized.IdentityBifunctorized]]
    * delegating to [[izumi.functional.bio.impl.MiniBIO.IOForMiniBIO]] via cast.
    *
    * UNLIKE [[bifunctorIsAlreadyBifunctor]], this factory does NOT erase to the type-level identity
    * of `Identity` (which would be the runtime carrier `A`). Instead, the runtime carrier of every
    * [[Bifunctorized.IdentityBifunctorized]] value is a [[izumi.functional.bio.impl.MiniBIO MiniBIO]]
    * (boxed), so the `IO2[MiniBIO]` dictionary is directly applicable. The cast is sound because
    * `Bifunctorized.IdentityBifunctorized` is an abstract type erased to `Object`, identical in
    * representation to `MiniBIO[E, A]`.
    *
    * Returned as `Predefined.Of` so it outranks any cats-effect `Sync[Identity]`-mediated path
    * that some user might bring into scope (none currently exists, but it costs nothing to be safe).
    */
  @inline implicit final def identityBifunctorizedHasIO2: Predefined.Of[IO2[Bifunctorized.IdentityBifunctorized]] =
    Predefined(MiniBIO.IOForMiniBIO.asInstanceOf[IO2[Bifunctorized.IdentityBifunctorized]])

}
