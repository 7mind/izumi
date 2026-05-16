package izumi.distage

import distage.LocatorPrivacy
import izumi.distage.bootstrap.BootstrapRootsMode
import izumi.distage.model.definition.{Activation, BootstrapContextModule, BootstrapModule}
import izumi.functional.bio.{Bifunctorize, Bifunctorized, IO2, Primitives2}
import izumi.distage.model.recursive.Bootloader
import izumi.distage.model.reflection.DIKey
import izumi.distage.model.{Injector, Locator, PlannerInput}
import izumi.distage.modules.DefaultModule
import izumi.reflect.{TagK, TagKK}

trait InjectorFactory {

  /**
    * Create a new Injector
    *
    * @tparam F                   The bifunctor effect type to use for effect and resource bindings and the result of [[izumi.distage.model.Injector#produce]]
    *
    * @param bootstrapBase        Initial bootstrap context module, such as [[izumi.distage.bootstrap.BootstrapLocator.defaultBootstrap]]
    *
    * @param bootstrapActivation  A map of axes of configuration to choices along these axes for the bootstrap environment.
    *                             The passed activation will affect _only_ the bootstrapping of the `Injector` itself (see [[izumi.distage.bootstrap.BootstrapLocator]]).
    *                             To set activation choices for subsequent injections, pass `Activation` to the methods of the created `Injector`
    *
    * @param parent               If set, this locator will be used as parent for the bootstrap locator.
    *                             Use this parameter if you want to reuse components from another injection BUT also want to
    *                             recreate the bootstrap environment with new parameters. If you just want to reuse all components,
    *                             including the bootstrap environment, use [[inherit]]
    *
    * @param bootstrapOverrides   Overrides of Injector's own bootstrap environment - injector itself is constructed with DI.
    *                             They can be used to customize the Injector, e.g. by adding members to [[izumi.distage.model.planning.PlanningHook]] Set.
    */
  def apply[F[+_, +_]: IO2: Primitives2: TagKK: DefaultModule](
    parent: Option[Locator] = None,
    bootstrapBase: BootstrapContextModule = defaultBootstrap,
    bootstrapActivation: Activation = defaultBootstrapActivation,
    bootstrapOverrides: Seq[BootstrapModule] = Nil,
    locatorPrivacy: LocatorPrivacy = defaultBootstrapLocatorPrivacy,
    bootstrapRootsMode: BootstrapRootsMode = defaultBootstrapRootsMode,
  ): Injector[F]

  /**
    * Create a new default Injector with [[izumi.functional.bio.Bifunctorized.IdentityBifunctorized]] effect type
    * (lawful MiniBIO-backed carrier for plain synchronous Scala).
    *
    * Use `apply[F]()` variant to specify a different effect type
    */
  def apply(): Injector[Bifunctorized.IdentityBifunctorized]

  /**
    * Monofunctor convenience overload — accepts an effect type of kind `[_]` (e.g. `cats.effect.IO`,
    * `zio.Task`) and transparently lifts it to the bifunctor carrier `Bifunctorized[F, +_, +_]`
    * via the [[Bifunctorize]] typeclass. Maps to the bifunctor `apply[Bifunctorized[F, +_, +_]]`.
    *
    * Goal 3 (bifunctorization.md): "distage's Injector ... entrypoints are transparently
    * bifunctorized/de-bifunctorized for monofunctors."
    *
    * The varargs accept `BootstrapModule` overrides only; the named-args of the bifunctor
    * overload (parent, bootstrapBase, etc.) are not exposed on this monofunctor convenience
    * variant — users who need them can write `Injector[Bifunctorized[F, +_, +_]](...)` directly.
    *
    * @tparam F monofunctor effect type
    */
  def apply[F[_]](
    overrides: BootstrapModule*
  )(implicit bifunctorize1: Bifunctorize[F],
    tagF: TagK[F],
    tagFBif: TagKK[Bifunctorized[F, +_, +_]],
    IO2Bif: IO2[Bifunctorized[F, +_, +_]],
    Primitives2Bif: Primitives2[Bifunctorized[F, +_, +_]],
    defaultModule: DefaultModule[Bifunctorized[F, +_, +_]],
  ): Injector[Bifunctorized[F, +_, +_]] = {
    val _ = (bifunctorize1, tagF)
    apply[Bifunctorized[F, +_, +_]](
      bootstrapOverrides = overrides
    )(using IO2Bif, Primitives2Bif, tagFBif, defaultModule)
  }

  /**
    * Alias for `apply[F]` that doesn't add a [[DefaultModule]] for F into bindings.
    *
    * `distage-core` doesn't require bindings provided by DefaultModule, but some extensions,
    * such as `distage-framework-docker` expect them to be defined
    */
  final def withoutDefaultModule[F[+_, +_]: IO2: Primitives2: TagKK](
    parent: Option[Locator] = None,
    bootstrapBase: BootstrapContextModule = defaultBootstrap,
    bootstrapActivation: Activation = defaultBootstrapActivation,
    overrides: Seq[BootstrapModule] = Nil,
    locatorPrivacy: LocatorPrivacy = defaultBootstrapLocatorPrivacy,
    bootstrapRootsMode: BootstrapRootsMode = defaultBootstrapRootsMode,
  ): Injector[F] = {
    apply[F](
      bootstrapBase = bootstrapBase,
      bootstrapActivation = bootstrapActivation,
      parent = parent,
      bootstrapOverrides = overrides,
      locatorPrivacy = locatorPrivacy,
      bootstrapRootsMode = bootstrapRootsMode,
    )(using IO2[F], Primitives2[F], TagKK[F], DefaultModule.empty[F])
  }

  /**
    * Create a new injector inheriting configuration, hooks and the object graph from a previous injection.
    *
    * @tparam F the bifunctor effect type to use for effect and resource bindings and the result of [[izumi.distage.model.Injector#produce]]
    *
    * @param parent Instances from parent [[izumi.distage.model.Locator]] will be available as imports in new Injector's [[izumi.distage.model.Producer#produce produce]]
    */
  def inherit[F[+_, +_]: IO2: Primitives2: TagKK](parent: Locator): Injector[F]

  /**
    * Monofunctor convenience overload — accepts an effect type of kind `[_]` and lifts to the
    * bifunctor carrier `Bifunctorized[F, +_, +_]` via the [[Bifunctorize]] typeclass.
    *
    * @tparam F monofunctor effect type
    */
  def inherit[F[_]](parent: Locator)(implicit
    bifunctorize1: Bifunctorize[F],
    tagF: TagK[F],
    tagFBif: TagKK[Bifunctorized[F, +_, +_]],
    IO2Bif: IO2[Bifunctorized[F, +_, +_]],
    Primitives2Bif: Primitives2[Bifunctorized[F, +_, +_]],
  ): Injector[Bifunctorized[F, +_, +_]] = {
    val _ = (bifunctorize1, tagF)
    inherit[Bifunctorized[F, +_, +_]](parent)(using IO2Bif, Primitives2Bif, tagFBif)
  }

  /**
    * Create a new injector inheriting configuration, hooks and the object graph from a previous injection.
    *
    * Unlike [[inherit]] this will fully (re)create the `defaultModule` in subsequent injections,
    * without reusing the existing instances in `parent`.
    *
    * @tparam F the bifunctor effect type to use for effect and resource bindings and the result of [[izumi.distage.model.Injector#produce]]
    *
    * @param parent Instances from parent [[izumi.distage.model.Locator]] will be available as imports in new Injector's [[izumi.distage.model.Producer#produce produce]]
    */
  def inheritWithNewDefaultModule[F[+_, +_]: IO2: Primitives2: TagKK](parent: Locator, defaultModule: DefaultModule[F]): Injector[F]

  /** Keys summonable by default in DI, *including* those added additionally by [[izumi.distage.modules.DefaultModule]] */
  def providedKeys[F[+_, +_]: DefaultModule](bootstrapOverrides: BootstrapModule*): Set[DIKey]
  def providedKeys[F[+_, +_]: DefaultModule](bootstrapBase: BootstrapContextModule, bootstrapOverrides: BootstrapModule*): Set[DIKey]

  def bootloader[F[+_, +_]](
    bootstrapModule: BootstrapModule,
    bootstrapActivation: Activation,
    defaultModule: DefaultModule[F],
    input: PlannerInput,
  ): Bootloader = {
    new Bootloader(this, bootstrapModule, bootstrapActivation, defaultModule.module, input)
  }

  protected def defaultBootstrap: BootstrapContextModule
  protected def defaultBootstrapActivation: Activation
  protected def defaultBootstrapLocatorPrivacy: LocatorPrivacy
  protected def defaultBootstrapRootsMode: BootstrapRootsMode
}

private[distage] object InjectorFactory {
  // No companion-object methods; the trait is implemented in `Injector`.
}
