package izumi.distage

import izumi.distage.model.definition.{Activation, BootstrapContextModule, BootstrapModule}
import izumi.distage.model.recursive.Bootloader
import izumi.distage.model.{Injector, Locator, PlannerInput}

trait InjectorFactory {

  /**
    * Create a new Injector
    *
    * @param overrides Optional: Overrides of Injector's own bootstrap environment - injector itself is constructed with DI.
    *                  They can be used to extend the Injector, e.g. add ability to inject config values
    */
  def apply(overrides: BootstrapModule*): Injector

  /**
    * Create a new Injector with chosen [[izumi.distage.model.definition.Activation]] axes
    *
    * @param activation A map of axes of configuration to choices along these axes
    * @param overrides Optional: Overrides of Injector's own bootstrap environment - injector itself is constructed with DI.
    *                  They can be used to extend the Injector, e.g. add ability to inject config values
    */
  def apply(activation: Activation, overrides: BootstrapModule*): Injector

  /**
    * Create a new Injector from a custom [[BootstrapContextModule]]
    *
    * @param bootstrapBase See [[izumi.distage.bootstrap.BootstrapLocator.defaultBootstrap]]
    * @param overrides     Optional: Overrides of Injector's own bootstrap environment - injector itself is constructed with DI.
    *                      They can be used to extend the Injector, e.g. add ability to inject config values
    */
  def apply(bootstrapBase: BootstrapContextModule, overrides: BootstrapModule*): Injector

  /**
    * Create a new Injector from a custom [[BootstrapContextModule]]
    *
    * @param activation A map of axes of configuration to choices along these axes
    * @param bootstrapBase See [[izumi.distage.bootstrap.BootstrapLocator.defaultBootstrap]]
    * @param overrides     Optional: Overrides of Injector's own bootstrap environment - injector itself is constructed with DI.
    *                      They can be used to extend the Injector, e.g. add ability to inject config values
    */
  def apply(activation: Activation, bootstrapBase: BootstrapContextModule, overrides: BootstrapModule*): Injector

  /**
    * Create a new injector inheriting configuration, hooks and the object graph from results of a previous Injector's run
    *
    * @param parent Instances from parent [[Locator]] will be available as imports in new Injector's [[izumi.distage.model.Producer#produce produce]]
    */
  def inherit(parent: Locator): Injector

  def bootloader(
    input: PlannerInput,
    activation: Activation = Activation.empty,
    bootstrapModule: BootstrapModule = BootstrapModule.empty,
  ): Bootloader = {
    new Bootloader(bootstrapModule, activation, input, this)
  }
}
