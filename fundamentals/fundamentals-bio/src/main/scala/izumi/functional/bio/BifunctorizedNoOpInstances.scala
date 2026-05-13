package izumi.functional.bio

import izumi.functional.bio.PredefinedHelper.Predefined

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

}
