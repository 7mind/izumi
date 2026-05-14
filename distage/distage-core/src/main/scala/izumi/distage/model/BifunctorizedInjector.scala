package izumi.distage.model

import izumi.distage.model.definition.BootstrapModule
import izumi.distage.modules.DefaultModule
import izumi.functional.bio.{Bifunctorized, IO2}
import izumi.functional.quasi.QuasiIO
import izumi.reflect.TagKK

/** Parallel BIO-friendly entry to distage's [[Injector]]. Construct an injector for a
  * bifunctor `F[+_, +_]` carrying an `IO2[Bifunctorized.NoOp[F, +_, +_]]` — i.e. any
  * registered BIO bifunctor (ZIO, MiniBIO, MonixBIO, plus the cats-effect-mediated path).
  *
  * Internally, derives a `QuasiIO[F[Throwable, _]]` from the BIO instance via the existing
  * `QuasiIO.fromBIO` route (see [[izumi.functional.lifecycle.LifecycleBifunctorized]] for the
  * precedent) and delegates to the existing [[Injector]] factory.
  *
  * Existing monofunctor `Injector[F[_]: QuasiIO]` callers are unaffected — this is an
  * additive parallel surface. M5 (where Quasi* is deleted) replaces the monofunctor
  * Injector with this BIO-constrained one as the default.
  */
object BifunctorizedInjector {

  /** Reinterpret a `QuasiIO[Bifunctorized.NoOp[F, Throwable, _]]` (obtained via the existing
    * `QuasiIO.fromBIO` derivation) as a `QuasiIO[F[Throwable, _]]`. Sound because
    * `Bifunctorized.NoOp[F, Throwable, A]` is erased to `F[Throwable, A]` — every method on
    * the dictionary takes/returns values that ARE `F[Throwable, ?]` at the JVM level.
    */
  @inline private def asQuasiIO[F[+_, +_]](
    implicit F: IO2[Bifunctorized.NoOp[F, +_, +_]]
  ): QuasiIO[F[Throwable, _]] = {
    val onWrapper: QuasiIO[Bifunctorized.NoOp[F, Throwable, _]] = implicitly[QuasiIO[Bifunctorized.NoOp[F, Throwable, _]]]
    onWrapper.asInstanceOf[QuasiIO[F[Throwable, _]]]
  }

  /** Create a new Injector for a BIO-constrained bifunctor `F[+_, +_]`. Delegates to
    * [[Injector.apply]] with a synthesized `QuasiIO[F[Throwable, _]]`.
    *
    * @see [[Injector.apply]] for the full parameter set; this overload exposes only the
    *      `bootstrapOverrides` knob — additional knobs (parent, bootstrapBase, activation,
    *      privacy, rootsMode) can be added in subsequent iterations if needed.
    */
  def apply[F[+_, +_]](
    overrides: BootstrapModule*
  )(implicit F: IO2[Bifunctorized.NoOp[F, +_, +_]],
    tagF: TagKK[F],
    defaultModule: DefaultModule[F[Throwable, _]],
  ): Injector[F[Throwable, _]] = {
    implicit val Q: QuasiIO[F[Throwable, _]] = asQuasiIO[F]
    Injector[F[Throwable, _]](bootstrapOverrides = overrides)
  }

  /** Create a new BIO-constrained injector inheriting configuration, hooks and the object
    * graph from a previous injection. Delegates to [[Injector.inherit]].
    */
  def inherit[F[+_, +_]](
    parent: Locator
  )(implicit F: IO2[Bifunctorized.NoOp[F, +_, +_]],
    tagF: TagKK[F],
  ): Injector[F[Throwable, _]] = {
    implicit val Q: QuasiIO[F[Throwable, _]] = asQuasiIO[F]
    Injector.inherit[F[Throwable, _]](parent)
  }

}
