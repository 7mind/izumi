package distage

import izumi.distage.bootstrap.{BootstrapLocator, Cycles}
import izumi.distage.model.definition.BootstrapContextModule
import izumi.distage.model.effect.DIEffect
import izumi.distage.model.recursive.Bootloader
import izumi.distage.modules.support.IdentitySupportModule
import izumi.distage.{InjectorDefaultImpl, InjectorFactory}
import izumi.fundamentals.platform.functional.Identity

object Injector extends InjectorFactory {

  /**
    * Create a new Injector
    *
    * @tparam F the effect type to use for effect and resource bindings and the result of [[izumi.distage.model.Injector#produce]]
    * @param overrides Optional: Overrides of Injector's own bootstrap environment - injector itself is constructed with DI.
    *                  They can be used to extend the Injector, e.g. add ability to inject config values
    */
  override def apply[F[_]: DIEffect: TagK: DefaultModule](overrides: BootstrapModule*): Injector[F] = {
    bootstrap(BootstrapLocator.defaultBootstrap, BootstrapLocator.defaultBootstrapActivation, overrides.merge)
  }

  /**
    * Create a new Injector from a custom [[izumi.distage.model.definition.BootstrapContextModule]]
    *
    * @tparam F the effect type to use for effect and resource bindings and the result of [[izumi.distage.model.Injector#produce]]
    * @param bootstrapBase See [[BootstrapLocator.defaultBootstrap]]
    * @param overrides     Optional: Overrides of Injector's own bootstrap environment - injector itself is constructed with DI.
    *                      They can be used to extend the Injector, e.g. add ability to inject config values
    */
  override def apply[F[_]: DIEffect: TagK: DefaultModule](bootstrapBase: BootstrapContextModule, overrides: BootstrapModule*): Injector[F] = {
    bootstrap(bootstrapBase, BootstrapLocator.defaultBootstrapActivation, overrides.merge)
  }

  /**
    * Create a default Injector with [[izumi.fundamentals.platform.functional.Identity]] effect type
    *
    * Use [[apply[F[_]*]] variants to specify a different effect type
    */
  override final def apply(): Injector[Identity] = apply[Identity]()

  /**
    * Create a new Injector with chosen [[izumi.distage.model.definition.Activation]] axes for the bootstrap environment.
    * The passed activation will affect _only_ the bootstrapping of the injector itself (see [[izumi.distage.bootstrap.BootstrapLocator]]),
    * to set activation choices, pass `Activation` to [[izumi.distage.model.Planner#plan]] or [[izumi.distage.model.PlannerInput]].
    *
    * @tparam F the effect type to use for effect and resource bindings and the result of [[izumi.distage.model.Injector#produce]]
    * @param activation A map of axes of configuration to choices along these axes
    * @param overrides Optional: Overrides of Injector's own bootstrap environment - injector itself is constructed with DI.
    *                  They can be used to extend the Injector, e.g. add ability to inject config values
    */
  override def withBootstrapActivation[F[_]: DIEffect: TagK: DefaultModule](activation: Activation, overrides: BootstrapModule*): Injector[F] = {
    bootstrap(BootstrapLocator.defaultBootstrap, BootstrapLocator.defaultBootstrapActivation ++ activation, overrides.merge)
  }

  /**
    * Create a new Injector from a custom [[izumi.distage.model.definition.BootstrapContextModule]].
    * The passed activation will affect _only_ the bootstrapping of the injector itself (see [[izumi.distage.bootstrap.BootstrapLocator]]),
    * to set activation choices, pass `Activation` to [[izumi.distage.model.Planner#plan]] or [[izumi.distage.model.PlannerInput]].
    *
    * @tparam F the effect type to use for effect and resource bindings and the result of [[izumi.distage.model.Injector#produce]]
    * @param activation A map of axes of configuration to choices along these axes
    * @param bootstrapBase See [[izumi.distage.bootstrap.BootstrapLocator.defaultBootstrap]]
    * @param overrides     Optional: Overrides of Injector's own bootstrap environment - injector itself is constructed with DI.
    *                      They can be used to extend the Injector, e.g. add ability to inject config values
    */
  override def withBootstrapActivation[F[_]: DIEffect: TagK: DefaultModule](
    activation: Activation,
    bootstrapBase: BootstrapContextModule,
    overrides: BootstrapModule*
  ): Injector[F] = {
    bootstrap(bootstrapBase, BootstrapLocator.defaultBootstrapActivation ++ activation, overrides.merge)
  }

  /**
    * Create a new injector inheriting configuration, hooks and the object graph from results of a previous Injector's run
    *
    * @tparam F the effect type to use for effect and resource bindings and the result of [[izumi.distage.model.Injector#produce]]
    * @param parent Instances from parent [[izumi.distage.model.Locator]] will be available as imports in new Injector's [[izumi.distage.model.Producer#produce produce]]
    */
  override def inherit[F[_]: DIEffect: TagK](parent: Locator): Injector[F] = {
    new InjectorDefaultImpl(parent, this, defaultModule = Module.empty)
  }

  override def inheritWithDefaultModule[F[_]: DIEffect: TagK](parent: Locator, defaultModule: Module): Injector[F] = {
    new InjectorDefaultImpl(parent, this, defaultModule = defaultModule ++ IdentitySupportModule) // Identity support always on
  }

  override def bootloader[F[_]](
    input: PlannerInput,
    activation: Activation,
    bootstrapModule: BootstrapModule,
    defaultModule: DefaultModule[F],
  ): Bootloader = {
    super.bootloader(input, activation, bootstrapModule, defaultModule)
  }

  /** Enable cglib proxies, but try to resolve cycles using by-name parameters if they can be used */
  def Standard: Injector.type = this

  /** Disable cglib proxies, allow only by-name parameters to resolve cycles */
  object NoProxies extends InjectorBootstrap(Cycles.Byname)

  /** Disable all cycle resolution, immediately throw when circular dependencies are found, whether by-name or not */
  object NoCycles extends InjectorBootstrap(Cycles.Disable)

  private[Injector] sealed abstract class InjectorBootstrap(cycleChoice: Cycles.AxisValueDef) extends InjectorFactory {
    override final def apply[F[_]: DIEffect: TagK: DefaultModule](overrides: BootstrapModule*): Injector[F] = {
      bootstrap(BootstrapLocator.defaultBootstrap, cycleActivation, overrides.merge)
    }

    override final def apply[F[_]: DIEffect: TagK: DefaultModule](bootstrapBase: BootstrapContextModule, overrides: BootstrapModule*): Injector[F] = {
      bootstrap(bootstrapBase, cycleActivation, overrides.merge)
    }

    override final def withBootstrapActivation[F[_]: DIEffect: TagK: DefaultModule](activation: Activation, overrides: BootstrapModule*): Injector[F] = {
      bootstrap(BootstrapLocator.defaultBootstrap, cycleActivation ++ activation, overrides.merge)
    }

    override final def apply(): Injector[Identity] = apply[Identity]()

    override final def withBootstrapActivation[F[_]: DIEffect: TagK: DefaultModule](
      activation: Activation,
      bootstrapBase: BootstrapContextModule,
      overrides: BootstrapModule*
    ): Injector[F] = {
      bootstrap(bootstrapBase, cycleActivation ++ activation, overrides.merge)
    }

    override final def inherit[F[_]: DIEffect: TagK](parent: Locator): Injector[F] = {
      new InjectorDefaultImpl(parent, this, Module.empty)
    }

    override final def inheritWithDefaultModule[F[_]: DIEffect: TagK](parent: Locator, defaultModule: Module): Injector[F] = {
      new InjectorDefaultImpl(parent, this, defaultModule)
    }

    private[this] def cycleActivation: Activation = Activation(Cycles -> cycleChoice)
  }

  private[this] def bootstrap[F[_]: DIEffect: TagK: DefaultModule](
    bootstrapBase: BootstrapContextModule,
    activation: Activation,
    overrides: BootstrapModule,
  ): Injector[F] = {
    val bootstrapLocator = new BootstrapLocator(bootstrapBase overriddenBy overrides, activation)
    val defaultModules = DefaultModule[F] ++ IdentitySupportModule // Identity support always on
    new InjectorDefaultImpl(bootstrapLocator, this, defaultModules)
  }

}
