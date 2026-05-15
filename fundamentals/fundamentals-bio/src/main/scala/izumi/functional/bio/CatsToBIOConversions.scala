package izumi.functional.bio

import izumi.functional.bio.PredefinedHelper.NotPredefined
import izumi.functional.bio.impl.CatsToBIO
import izumi.reflect.TagK

import scala.language.implicitConversions

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
  * This object ALSO provides transparent (de-)submerging implicit conversions
  * [[bifunctorizeSubmerging]] / [[debifunctorizeUnSubmerging]] that fire at
  * expected-type sites for any monofunctor `F[_]` with a `cats.ApplicativeError[F, Throwable]`
  * in scope. They are imported into the user's scope alongside the
  * `AsyncToBIO`/`PrimitivesToBIO` summoners, so a user who reaches for the cats-mediated
  * BIO surface gets transparent submerging on the conversion seams "for free"
  * (matching the spec text in `bifunctorization.md` §"Conversion of effect values":
  * "the Throwable error must be Submerged, converted into a typed error during `bifunctorize`"
  * and "In `debifunctorize`, a typed error must be de-Submerged").
  *
  * Priority: these implicit conversions live in the user's import scope, which
  * outranks the cats-free identity conversions
  * [[Bifunctorized.bifunctorizeConversion]] / [[Bifunctorized.debifunctorizeConversion]]
  * (companion-of-RHS-of-alias). Real-bifunctor users typically do NOT import
  * `CatsToBIOConversions._` at all (they consume `IO2[F]` etc. directly), so
  * the Goal-4 zero-cost path through `Bifunctorized.bifunctorize` (method, not
  * conversion) remains identity.
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

  /** Cats-mediated transparent submerging at conversion seams.
    *
    * Implicit lift of `F[A]` to `Bifunctorized[F, Throwable, A]` that, UNLIKE the
    * cats-free [[Bifunctorized.bifunctorizeConversion]], submerges the raw monofunctor
    * Throwable channel into a typed BIO error channel via [[SubmergedTypedError]].
    *
    * Required for the spec text in `bifunctorization.md` §"Conversion of effect values":
    * "the Throwable error must be Submerged, converted into a typed error during `bifunctorize`."
    *
    * Idempotency: [[SubmergedTypedError.apply]] is idempotent on TagK match (no double
    * wrapping for same-`F` re-bifunctorization). Defects (introduced via BIO `terminate`
    * or `sync(throw …)`) reach this conversion only if they bypass the BIO instance
    * methods — but practically users construct typed effects through BIO and only round-trip
    * through this conversion at boundary sites.
    *
    * Resolution priority: this conversion is in the user's import scope when they
    * `import izumi.functional.bio.CatsToBIOConversions.*`. Import scope outranks
    * the cats-free [[Bifunctorized.bifunctorizeConversion]] (companion-of-RHS), so
    * cats-mediated submerging wins for any `F[_]` that has both `ApplicativeError`
    * AND a `TagK`. For real bifunctors that don't go through this import, the
    * cats-free identity conversion remains active.
    */
  @inline implicit final def bifunctorizeSubmerging[F[_], A](
    fa: F[A]
  )(implicit F: cats.ApplicativeError[F, Throwable],
    tag: TagK[F],
  ): Bifunctorized[F, Throwable, A] =
    Bifunctorized.assert(F.adaptError(fa) { case t: Throwable => SubmergedTypedError[F](t) })

  /** Inverse of [[bifunctorizeSubmerging]]: implicit projection of
    * `Bifunctorized[F, Throwable, A]` to `F[A]` that un-submerges the typed BIO
    * error channel back into the raw monofunctor Throwable channel.
    *
    * Required for the spec text in `bifunctorization.md` §"Conversion of effect values":
    * "In `debifunctorize`, a typed error must be de-Submerged, unwrapped, as its expected
    * to be in order for monofunctor's native methods to work with it."
    *
    * Pattern: `F.adaptError(b.unwrap) { case SubmergedTypedError(payload: Throwable) => payload }`
    * — only same-`TagK[F]` SubmergedTypedErrors are unwrapped (via
    * [[SubmergedTypedError.unapply]]); other Throwables (defects, foreign-F submerged errors)
    * pass through unchanged.
    *
    * Resolution priority: same as [[bifunctorizeSubmerging]] — import scope outranks
    * [[Bifunctorized.debifunctorizeConversion]] companion-of-RHS.
    */
  @inline implicit final def debifunctorizeUnSubmerging[F[_], A](
    b: Bifunctorized[F, Throwable, A]
  )(implicit F: cats.ApplicativeError[F, Throwable],
    tag: TagK[F],
  ): F[A] =
    F.adaptError(b.asInstanceOf[F[A]]) {
      case SubmergedTypedError(payload: Throwable) => payload
    }

}
