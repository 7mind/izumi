package distage

import izumi.distage.bootstrap.{BootstrapLocator, Cycles}
import izumi.distage.model.definition
import izumi.distage.model.definition.BootstrapContextModule
import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.recursive.Bootloader
import izumi.distage.modules.support.IdentitySupportModule
import izumi.distage.{InjectorDefaultImpl, InjectorFactory}
import izumi.fundamentals.platform.functional.Identity

object Injector extends InjectorFactory {

  /**
    * Create a new Injector
    *
    * @tparam F the effect type to use for effect and resource bindings and the result of [[izumi.distage.model.Injector#produce]]
    *
    * @param overrides Optional: Overrides of Injector's own bootstrap environment - injector itself is constructed with DI.
    *                  They can be used to customize the Injector, e.g. by adding members to [[izumi.distage.model.planning.PlanningHook]] Set.
    */
  override def apply[F[_]: QuasiIO: TagK: DefaultModule](
    overrides: BootstrapModule*
  ): Injector[F] = {
    bootstrap(this, defaultBootstrap, defaultBootstrapActivation, None, overrides)
  }

  /**
    * Create a new Injector with custom parameters [[izumi.distage.model.definition.BootstrapContextModule]]
    *
    * @tparam F                   The effect type to use for effect and resource bindings and the result of [[izumi.distage.model.Injector#produce]]
    *
    * @param bootstrapBase        Initial bootstrap context module, such as [[izumi.distage.bootstrap.BootstrapLocator.defaultBootstrap]]
    *
    * @param bootstrapActivation  A map of axes of configuration to choices along these axes.
    *                             The passed activation will affect _only_ the bootstrapping of the `Injector` itself (see [[izumi.distage.bootstrap.BootstrapLocator]]).
    *                             To set activation choices for subsequent injections, pass `Activation` to the methods of the created `Injector`
    *
    * @param parent               If set, this locator will be used as parent for the bootstrap locator.
    *                             Use this parameter if you want to reuse components from another injection BUT also want to
    *                             recreate the bootstrap environment with new parameters. If you just want to reuse all components,
    *                             including the bootstrap environment, use [[inherit]]
    *
    * @param overrides            Overrides of Injector's own bootstrap environment - injector itself is constructed with DI.
    *                             They can be used to customize the Injector, e.g. by adding members to [[izumi.distage.model.planning.PlanningHook]] Set.
    */
  override def apply[F[_]: QuasiIO: TagK: DefaultModule](
    bootstrapBase: BootstrapContextModule = defaultBootstrap,
    bootstrapActivation: Activation = defaultBootstrapActivation,
    parent: Option[Locator] = None,
    overrides: Seq[BootstrapModule] = Nil,
  ): Injector[F] = {
    bootstrap(this, bootstrapBase, defaultBootstrapActivation ++ bootstrapActivation, parent, overrides)
  }

  /**
    * Create a default Injector with [[izumi.fundamentals.platform.functional.Identity]] effect type
    *
    * Use `apply[F]()` variant to specify a different effect type
    */
  override def apply(): Injector[Identity] = apply[Identity]()

  /**
    * Create a new injector inheriting configuration, hooks and the object graph from a previous injection.
    *
    * @tparam F the effect type to use for effect and resource bindings and the result of [[izumi.distage.model.Injector#produce]]
    *
    * @param parent Instances from parent [[izumi.distage.model.Locator]] will be available as imports in new Injector's [[izumi.distage.model.Producer#produce produce]]
    */
  override def inherit[F[_]: QuasiIO: TagK](parent: Locator): Injector[F] = {
    new InjectorDefaultImpl(this, parent, definition.Module.empty)
  }

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
  override def inheritWithNewDefaultModule[F[_]: QuasiIO: TagK](parent: Locator, defaultModule: DefaultModule[F]): Injector[F] = {
    inheritWithNewDefaultModuleImpl(this, parent, defaultModule)
  }

  override def providedKeys[F[_]: DefaultModule](overrides: BootstrapModule*): Set[DIKey] = {
    providedKeys[F](defaultBootstrap, overrides: _*)
  }

  override def providedKeys[F[_]: DefaultModule](bootstrapBase: BootstrapContextModule, overrides: BootstrapModule*): Set[DIKey] = {
    (bootstrapBase.keysIterator ++
    overrides.iterator.flatMap(_.keysIterator) ++
    BootstrapLocator.selfReflectionKeys.iterator ++
    IdentitySupportModule.keysIterator ++
    DefaultModule[F].keysIterator ++
    InjectorDefaultImpl.providedKeys.iterator).toSet
  }

  override def bootloader[F[_]](
    bootstrapModule: BootstrapModule,
    bootstrapActivation: Activation,
    defaultModule: DefaultModule[F],
    input: PlannerInput,
  ): Bootloader = {
    super.bootloader(bootstrapModule, bootstrapActivation, defaultModule, input)
  }

  /** Enable cglib proxies, but try to resolve cycles using by-name parameters if they can be used */
  def Standard: Injector.type = this

  /** Disable cglib proxies, allow only by-name parameters to resolve cycles */
  object NoProxies extends InjectorBootstrap(Cycles.Byname)

  /** Disable all cycle resolution, immediately throw when circular dependencies are found, whether by-name or not */
  object NoCycles extends InjectorBootstrap(Cycles.Disable)

  private[Injector] sealed abstract class InjectorBootstrap(
    cycleChoice: Cycles.AxisChoiceDef
  ) extends InjectorFactory {
    override final def apply[F[_]: QuasiIO: TagK: DefaultModule](overrides: BootstrapModule*): Injector[F] = {
      bootstrap(this, defaultBootstrap, defaultBootstrapActivation, None, overrides)
    }

    override final def apply[F[_]: QuasiIO: TagK: DefaultModule](
      bootstrapBase: BootstrapContextModule,
      bootstrapActivation: Activation,
      parent: Option[Locator],
      overrides: Seq[BootstrapModule],
    ): Injector[F] = {
      bootstrap(this, bootstrapBase, defaultBootstrapActivation ++ bootstrapActivation, parent, overrides)
    }

    override final def apply(): Injector[Identity] = apply[Identity]()

    override final def inherit[F[_]: QuasiIO: TagK](parent: Locator): Injector[F] = {
      new InjectorDefaultImpl(this, parent, definition.Module.empty)
    }

    override final def inheritWithNewDefaultModule[F[_]: QuasiIO: TagK](parent: Locator, defaultModule: DefaultModule[F]): Injector[F] = {
      inheritWithNewDefaultModuleImpl(this, parent, defaultModule)
    }

    override def providedKeys[F[_]: DefaultModule](overrides: BootstrapModule*): Set[DIKey] = {
      Injector.providedKeys[F](overrides: _*)
    }

    override def providedKeys[F[_]: DefaultModule](bootstrapBase: BootstrapContextModule, overrides: BootstrapModule*): Set[DIKey] = {
      Injector.providedKeys[F](bootstrapBase, overrides: _*)
    }

    override protected[this] final def defaultBootstrap: BootstrapContextModule = BootstrapLocator.defaultBootstrap
    override protected[this] final def defaultBootstrapActivation: Activation = definition.Activation(Cycles -> cycleChoice)
  }

  private[this] def bootstrap[F[_]: QuasiIO: TagK: DefaultModule](
    injectorFactory: InjectorFactory,
    bootstrapBase: BootstrapContextModule,
    activation: Activation,
    parent: Option[Locator],
    overrides: Seq[BootstrapModule],
  ): Injector[F] = {
    val bootstrapLocator = BootstrapLocator.bootstrap(bootstrapBase, activation, overrides, parent)
    inheritWithNewDefaultModuleImpl(injectorFactory, bootstrapLocator, implicitly)
  }

  private[this] def inheritWithNewDefaultModuleImpl[F[_]: QuasiIO: TagK](
    injectorFactory: InjectorFactory,
    parent: Locator,
    defaultModule: DefaultModule[F],
  ): Injector[F] = {
    val defaultModule0 = defaultModule.module ++ IdentitySupportModule // Identity support is always on
    new InjectorDefaultImpl(injectorFactory, parent, defaultModule = defaultModule0)
  }

  @inline override protected[this] def defaultBootstrap: BootstrapContextModule = BootstrapLocator.defaultBootstrap
  @inline override protected[this] def defaultBootstrapActivation: Activation = BootstrapLocator.defaultBootstrapActivation

}
