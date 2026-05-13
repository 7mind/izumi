package izumi.functional.bio

import izumi.functional.bio.PredefinedHelper.NotPredefined
import izumi.functional.bio.impl.CatsToBIO
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

}
