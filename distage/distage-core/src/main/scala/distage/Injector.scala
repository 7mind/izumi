package distage

import izumi.distage.InjectorDefaultImpl
import izumi.distage.bootstrap.{BootstrapLocator, CglibBootstrap}
import izumi.distage.model.definition.BootstrapContextModule

object Injector {

  /**
    * Create a new Injector
    *
    * @param overrides Optional: Overrides of Injector's own bootstrap environment - injector itself is constructed with DI.
    *                  They can be used to extend the Injector, e.g. add ability to inject config values
    */
  def apply(overrides: BootstrapModule*): Injector = {
    apply(CglibBootstrap.cogenBootstrap, overrides: _ *)
  }

  /**
    * Create a new Injector with chosen [[izumi.distage.model.definition.Activation]] axes
    *
    * @param overrides Optional: Overrides of Injector's own bootstrap environment - injector itself is constructed with DI.
    *                  They can be used to extend the Injector, e.g. add ability to inject config values
    */
  def apply(activation: Activation, overrides: BootstrapModule*): Injector = {
    apply(CglibBootstrap.cogenBootstrap, overrides :+ activationModule(activation): _ *)
  }

  /**
    * Create a new Injector from a custom [[BootstrapContextModule]]
    *
    * @param bootstrapBase See [[BootstrapLocator]] and [[CglibBootstrap]] for a list available bootstrap modules
    * @param overrides     Optional: Overrides of Injector's own bootstrap environment - injector itself is constructed with DI.
    *                      They can be used to extend the Injector, e.g. add ability to inject config values
    */
  def apply(bootstrapBase: BootstrapContextModule, overrides: BootstrapModule*): Injector = {
    bootstrap(bootstrapBase, overrides.merge)
  }

  /**
    * Create a new injector inheriting configuration, hooks and the object graph from results of a previous Injector's run
    *
    * @param parent Instances from parent [[Locator]] will be available as imports in new Injector's [[izumi.distage.model.Producer#produce produce]]
    */
  def inherit(parent: Locator): Injector = {
    new InjectorDefaultImpl(parent)
  }

  private[this] def bootstrap(bootstrapBase: BootstrapContextModule, overrides: BootstrapModule): Injector = {
    val bootstrapDefinition = bootstrapBase.overridenBy(overrides)
    val bootstrapLocator = new BootstrapLocator(bootstrapDefinition)
    inherit(bootstrapLocator)
  }

  object Standard extends InjectorBootstrap {
    override def apply(): Injector = super.apply()
    override def apply(overrides: BootstrapModule*): Injector = {
      bootstrap(CglibBootstrap.cogenBootstrap, overrides = overrides.merge)
    }
  }

  object NoProxies extends InjectorBootstrap {
    override def apply(): Injector = super.apply()
    override def apply(overrides: BootstrapModule*): Injector = {
      bootstrap(BootstrapLocator.noProxiesBootstrap, overrides = overrides.merge)
    }
  }

  object NoCycles extends InjectorBootstrap {
    override def apply(): Injector = super.apply()
    override def apply(overrides: BootstrapModule*): Injector = {
      bootstrap(BootstrapLocator.noCyclesBootstrap, overrides = overrides.merge)
    }
  }

  private[Injector] sealed trait InjectorBootstrap {
    def apply(overrides: BootstrapModule*): Injector
    def apply(): Injector = apply(overrides = Nil: _*)

    final def apply(activation: Activation, overrides: BootstrapModule*): Injector = {
      apply(overrides :+ activationModule(activation): _*)
    }
  }

  private[this] def activationModule(activation: Activation): BootstrapModuleDef = {
    new BootstrapModuleDef {
      make[Activation].fromValue(activation)
    }
  }

}
