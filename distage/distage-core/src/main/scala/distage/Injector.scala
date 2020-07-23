package distage

import izumi.distage.bootstrap.BootstrapLocator
import izumi.distage.model.definition.BootstrapContextModule
import izumi.distage.model.definition.StandardAxis.Cycles
import izumi.distage.model.recursive.Bootloader
import izumi.distage.{InjectorDefaultImpl, InjectorFactory}

object Injector extends InjectorFactory {

  /**
    * Create a new Injector
    *
    * @param overrides Optional: Overrides of Injector's own bootstrap environment - injector itself is constructed with DI.
    *                  They can be used to extend the Injector, e.g. add ability to inject config values
    */
  override def apply(overrides: BootstrapModule*): Injector = {
    bootstrap(BootstrapLocator.defaultBootstrap, BootstrapLocator.defaultBootstrapActivation, overrides.merge)
  }

  /**
    * Create a new Injector with chosen [[izumi.distage.model.definition.Activation]] axes
    *
    * @param activation A map of axes of configuration to choices along these axes
    * @param overrides  Optional: Overrides of Injector's own bootstrap environment - injector itself is constructed with DI.
    *                   They can be used to extend the Injector, e.g. add ability to inject config values
    */
  override def apply(activation: Activation, overrides: BootstrapModule*): Injector = {
    bootstrap(BootstrapLocator.defaultBootstrap, BootstrapLocator.defaultBootstrapActivation ++ activation, overrides.merge)
  }

  /**
    * Create a new Injector from a custom [[BootstrapContextModule]]
    *
    * @param bootstrapBase See [[BootstrapLocator.defaultBootstrap]]
    * @param overrides     Optional: Overrides of Injector's own bootstrap environment - injector itself is constructed with DI.
    *                      They can be used to extend the Injector, e.g. add ability to inject config values
    */
  override def apply(bootstrapBase: BootstrapContextModule, overrides: BootstrapModule*): Injector = {
    bootstrap(bootstrapBase, BootstrapLocator.defaultBootstrapActivation, overrides.merge)
  }

  /**
    * Create a new Injector from a custom [[BootstrapContextModule]] and the chosen [[izumi.distage.model.definition.Activation]] axes
    *
    * @param activation A map of axes of configuration to choices along these axes
    * @param bootstrapBase See [[BootstrapLocator.defaultBootstrap]]
    * @param overrides     Optional: Overrides of Injector's own bootstrap environment - injector itself is constructed with DI.
    *                      They can be used to extend the Injector, e.g. add ability to inject config values
    */
  override def apply(activation: Activation, bootstrapBase: BootstrapContextModule, overrides: BootstrapModule*): Injector = {
    bootstrap(bootstrapBase, BootstrapLocator.defaultBootstrapActivation ++ activation, overrides.merge)
  }

  /**
    * Create a new injector inheriting configuration, hooks and the object graph from results of a previous Injector's run
    *
    * @param parent Instances from parent [[Locator]] will be available as imports in new Injector's [[izumi.distage.model.Producer#produce produce]]
    */
  override def inherit(parent: Locator): Injector = {
    new InjectorDefaultImpl(parent, this)
  }

  override def bootloader(input: PlannerInput, activation: Activation = Activation.empty, bootstrapModule: BootstrapModule = BootstrapModule.empty): Bootloader = {
    super.bootloader(input, activation, bootstrapModule)
  }

  /** Enable cglib proxies, but try to resolve cycles using by-name parameters if they can be used */
  object Standard extends InjectorBootstrap(Cycles.Proxy)

  /** Disable cglib proxies, allow only by-name parameters to resolve cycles */
  object NoProxies extends InjectorBootstrap(Cycles.Byname)

  /** Disable all cycle resolution, immediately throw when circular dependencies are found, whether by-name or not */
  object NoCycles extends InjectorBootstrap(Cycles.Disable)

  private[Injector] sealed abstract class InjectorBootstrap(cycleChoice: Cycles.AxisValueDef) extends InjectorFactory {
    override final def apply(overrides: BootstrapModule*): Injector = {
      bootstrap(BootstrapLocator.defaultBootstrap, cycleActivation, overrides.merge)
    }

    override final def apply(activation: Activation, overrides: BootstrapModule*): Injector = {
      bootstrap(BootstrapLocator.defaultBootstrap, cycleActivation ++ activation, overrides.merge)
    }

    override final def apply(bootstrapBase: BootstrapContextModule, overrides: BootstrapModule*): Injector = {
      bootstrap(bootstrapBase, cycleActivation, overrides.merge)
    }

    override final def apply(activation: Activation, bootstrapBase: BootstrapContextModule, overrides: BootstrapModule*): Injector = {
      bootstrap(bootstrapBase, cycleActivation ++ activation, overrides.merge)
    }

    override final def inherit(parent: Locator): Injector = {
      new InjectorDefaultImpl(parent, this)
    }

    private[this] def cycleActivation: Activation = Activation(Cycles -> cycleChoice)
  }

  private[this] def bootstrap(bootstrapBase: BootstrapContextModule, activation: Activation, overrides: BootstrapModule): Injector = {
    val bootstrapLocator = new BootstrapLocator(bootstrapBase.overridenBy(overrides), activation)
    inherit(bootstrapLocator)
  }

}
