package izumi.functional.bio

import izumi.functional.bio.PredefinedHelper.NotPredefined
import izumi.functional.bio.impl.CatsToBIO
import izumi.fundamentals.orphans.`cats.ApplicativeError`
import izumi.reflect.TagK

/** CE → BIO implicit-conversion ladder. Users opt in via
  * `import izumi.functional.bio.CatsToBIOConversions.*`. Each instance returns
  * [[PredefinedHelper.NotPredefined.Of]] so the implicit-priority machinery in
  * [[Root]] can prefer pre-defined BIO instances (ZIO, MiniBIO, Either) over the
  * cats-derived fallback.
  *
  * PR-04 ships only the `AsyncToBIO` instance. Weaker conversions (Sync→IO2,
  * Monad→Monad2, etc.) require their own factories paralleling
  * [[impl.CatsToBIO.asyncToBIO]]; those are deferred. See `[QUESTION]` note in the
  * PR-04 plan.
  *
  * Goal 5 ("No-More-Orphans"): this file is NOT mixed into the `bio` package
  * object — users must explicitly import it to bring cats onto their classpath.
  *
  * This object ALSO provides a cats-mediated [[Bifunctorize]] typeclass instance
  * [[bifunctorizeForCatsApplicativeError]] that, when imported into the user's scope,
  * outranks the identity [[Bifunctorize]] instance from the companion of
  * [[Bifunctorize]] and submerges / de-submerges the raw monofunctor Throwable
  * channel into / out of a typed BIO error channel via [[SubmergedTypedError]]
  * (matching `bifunctorization.md` §"Conversion of effect values":
  * "the Throwable error must be Submerged, converted into a typed error during `bifunctorize`"
  * and "In `debifunctorize`, a typed error must be de-Submerged").
  *
  * The instance uses the "No-More-Orphans" trick (`izumi.fundamentals.orphans.\`cats.ApplicativeError\``)
  * so users without cats on their classpath are not forced to depend on it: the
  * phantom-typeclass parameter only resolves when `cats.ApplicativeError` is on the classpath.
  *
  * Priority: this typeclass instance lives in the user's import scope, which outranks
  * the identity instance in the companion of [[Bifunctorize]]. Real-bifunctor users
  * typically do NOT import `CatsToBIOConversions._` at all (they consume `IO2[F]` etc.
  * directly), so the Goal-4 zero-cost path through `Bifunctorized.bifunctorize` remains
  * identity for them.
  */
object CatsToBIOConversions {

  /** Derive a BIO `Async2[Bifunctorized[F, +_, +_]]` from `cats.effect.kernel.Async[F]`
    * and `izumi.reflect.TagK[F]`. The `TagK[F]` is used to discriminate submerged
    * typed errors per source-monofunctor (see [[SubmergedTypedError]]).
    *
    * Returns the broadest instance — the BIO typeclass intersection from
    * [[impl.CatsToBIO.asyncToBIO]] — downcast to `Async2` for the implicit-search
    * landing slot. Callers needing `Temporal2`/`Fork2`/`BlockingIO2`/`Primitives2`
    * can summon them via the same instance using `Root`'s `Attach*` accessors,
    * because the underlying instance carries all five.
    */
  @inline implicit final def AsyncToBIO[F[_]](
    implicit F: cats.effect.kernel.Async[F],
    tag: TagK[F],
  ): NotPredefined.Of[Async2[Bifunctorized[F, +_, +_]]] = {
    CatsToBIO.asyncToBIO[F].asInstanceOf[NotPredefined.Of[Async2[Bifunctorized[F, +_, +_]]]]
  }

  /** Sibling landing pad: same backing instance as [[AsyncToBIO]] downcast to `Primitives2`.
    *
    * The underlying instance from [[impl.CatsToBIO.asyncToBIO]] is
    * `Async2 & Temporal2 & Fork2 & BlockingIO2 & Primitives2 & Clock2`, but
    * [[AsyncToBIO]]'s declared return type is only `Async2`. Because `Primitives2`
    * is not a supertype of `Async2`, Scala's implicit search will not derive
    * `Primitives2[Bifunctorized[F, +_, +_]]` from the `Async2` instance — this
    * factory exposes the same backing value typed as `Primitives2` so it is
    * summonable independently. Required by `Injector[Bifunctorized[F, +_, +_]]`'s
    * `[F: IO2: Primitives2: TagKK: DefaultModule]` bound on the cats-effect side.
    */
  @inline implicit final def PrimitivesToBIO[F[_]](
    implicit F: cats.effect.kernel.Async[F],
    tag: TagK[F],
  ): NotPredefined.Of[Primitives2[Bifunctorized[F, +_, +_]]] = {
    CatsToBIO.asyncToBIO[F].asInstanceOf[NotPredefined.Of[Primitives2[Bifunctorized[F, +_, +_]]]]
  }

  /** Cats-mediated [[Bifunctorize]] instance providing transparent submerging /
    * un-submerging at the `F[A] <-> Bifunctorized[F, Throwable, A]` round-trip seam.
    *
    * Required for the spec text in `bifunctorization.md` §"Conversion of effect values":
    *
    *   "the Throwable error must be Submerged, converted into a typed error during `bifunctorize`."
    *
    *   "In `debifunctorize`, a typed error must be de-Submerged, unwrapped, as its expected
    *    to be in order for monofunctor's native methods to work with it."
    *
    * Submerge: `F.adaptError(fa) { case t: Throwable => SubmergedTypedError[F](t) }`.
    *   [[SubmergedTypedError.apply]] is idempotent on TagK match (no double wrapping for
    *   same-`F` re-bifunctorization).
    *
    * Un-submerge: `F.adaptError(b.unwrap) { case SubmergedTypedError(payload: Throwable) => payload }`.
    *   Only same-`TagK[F]` SubmergedTypedErrors are unwrapped (via [[SubmergedTypedError.unapply]]);
    *   other Throwables (defects, foreign-F submerged errors) pass through unchanged.
    *
    * Resolution priority: this instance lives in the user's import scope when they
    * `import izumi.functional.bio.CatsToBIOConversions.*`. Import scope outranks the
    * identity instance in the companion of [[Bifunctorize]], so cats-mediated submerging
    * wins for any `F[_]` that has both `cats.ApplicativeError[F, Throwable]` and a `TagK[F]`.
    * For real bifunctors used through their bifunctor surface (and any `F` whose
    * `ApplicativeError` is not imported), the identity instance from the [[Bifunctorize]]
    * companion remains active and `bifunctorize(realBifunctor) eq realBifunctor` holds (Goal 4).
    *
    * "No-More-Orphans" trick: the context-bound phantom typeclass
    * `\`cats.ApplicativeError\`` (from `izumi.fundamentals.orphans.OrphanDefs`) only
    * resolves when `cats.ApplicativeError` is on the user's classpath. Without cats,
    * the implicit is silently invisible to implicit search, so users on a no-cats
    * classpath do not inherit a cats dependency from `fundamentals-bio` (Goal 5).
    */
  @inline implicit final def bifunctorizeForCatsApplicativeError[F[_], ApplicativeError[_[_], _]: `cats.ApplicativeError`](
    implicit F0: ApplicativeError[F, Throwable],
    tag: TagK[F],
  ): Bifunctorize[F] = {
    val F = F0.asInstanceOf[cats.ApplicativeError[F, Throwable]]
    new Bifunctorize[F] {
      // The `tag` from the enclosing method is captured into this closure and reachable to
      // implicit search for `SubmergedTypedError[F].apply` / `.unapply` below.
      override def bifunctorize[A](fa: F[A]): Bifunctorized[F, Throwable, A] =
        Bifunctorized.assert(F.adaptError(fa) { case t: Throwable => SubmergedTypedError[F](t) })

      override def debifunctorize[A](b: Bifunctorized[F, Throwable, A]): F[A] =
        F.adaptError(b.asInstanceOf[F[A]]) {
          case SubmergedTypedError(payload: Throwable) => payload
        }
    }
  }

}
