package izumi.distage

import distage.LocatorPrivacy
import izumi.distage.bootstrap.BootstrapRootsMode
import izumi.distage.model.definition.{Activation, BootstrapContextModule, BootstrapModule}
import izumi.functional.quasi.QuasiIO
import izumi.distage.model.recursive.Bootloader
import izumi.distage.model.reflection.DIKey
import izumi.distage.model.{Injector, Locator, PlannerInput}
import izumi.distage.modules.DefaultModule
import izumi.fundamentals.platform.functional.Identity
import izumi.reflect.TagK

trait InjectorFactory {

  /**
    * Create a new Injector
    *
    * @tparam F                   The effect type to use for effect and resource bindings and the result of [[izumi.distage.model.Injector#produce]]
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
  def apply[F[_]: QuasiIO: TagK: DefaultModule](
    parent: Option[Locator] = None,
    bootstrapBase: BootstrapContextModule = defaultBootstrap,
    bootstrapActivation: Activation = defaultBootstrapActivation,
    bootstrapOverrides: Seq[BootstrapModule] = Nil,
    locatorPrivacy: LocatorPrivacy = defaultBootstrapLocatorPrivacy,
    bootstrapRootsMode: BootstrapRootsMode = defaultBootstrapRootsMode,
  ): Injector[F]

  /**
    * Create a new default Injector with [[izumi.fundamentals.platform.functional.Identity]] effect type
    *
    * Use `apply[F]()` variant to specify a different effect type
    *
    * @note this method exists only because of Scala 2.12's sub-par implicit handling:
    *       2.12 fails to default to `QuasiIO.quasiIOIdentity` when writing `Injector()` if cats-effect
    *       is on the classpath because of recursive (on 2.12: diverging) instances in `cats.effect.kernel.Sync` object
    */
  def apply(): Injector[Identity]

  /**
    * Alias for `apply[F]` that doesn't add a [[DefaultModule]] for F into bindings.
    *
    * `distage-core` doesn't require bindings provided by DefaultModule, but some extensions,
    * such as `distage-framework-docker` expect them to be defined
    */
  final def withoutDefaultModule[F[_]: QuasiIO: TagK](
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
    )(using QuasiIO[F], TagK[F], DefaultModule.empty[F])
  }

  /**
    * Create a new injector inheriting configuration, hooks and the object graph from a previous injection.
    *
    * @tparam F the effect type to use for effect and resource bindings and the result of [[izumi.distage.model.Injector#produce]]
    *
    * @param parent Instances from parent [[izumi.distage.model.Locator]] will be available as imports in new Injector's [[izumi.distage.model.Producer#produce produce]]
    */
  def inherit[F[_]: QuasiIO: TagK](parent: Locator): Injector[F]

  /**
    * Create a new injector inheriting configuration, hooks and the object graph from a previous injection.
    *
    * Unlike [[inherit]] this will fully (re)create the `defaultModule` in subsequent injections,
    * without reusing the existing instances in `parent`.
    *
    * @tparam F the effect type to use for effect and resource bindings and the result of [[izumi.distage.model.Injector#produce]]
    *
    * @param parent Instances from parent [[izumi.distage.model.Locator]] will be available as imports in new Injector's [[izumi.distage.model.Producer#produce produce]]
    */
  def inheritWithNewDefaultModule[F[_]: QuasiIO: TagK](parent: Locator, defaultModule: DefaultModule[F]): Injector[F]

  /** Keys summonable by default in DI, *including* those added additionally by [[izumi.distage.modules.DefaultModule]] */
  def providedKeys[F[_]: DefaultModule](bootstrapOverrides: BootstrapModule*): Set[DIKey]
  def providedKeys[F[_]: DefaultModule](bootstrapBase: BootstrapContextModule, bootstrapOverrides: BootstrapModule*): Set[DIKey]

  def bootloader[F[_]](
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
