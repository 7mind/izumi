package distage

import com.github.pshirshov.izumi.distage.InjectorDefaultImpl
import com.github.pshirshov.izumi.distage.bootstrap.{CglibBootstrap, DefaultBootstrapLocator}
import com.github.pshirshov.izumi.distage.model.definition.BootstrapContextModule

object Injector {

  /**
    * Create a new Injector
    *
    * @param overrides Optional: Overrides of Injector's own bootstrap environment - injector itself is constructed with DI.
    *                  They can be used to extend the Injector, e.g. add ability to inject config values
    */
  def apply(overrides: BootstrapModule*): Injector = {
    apply(CglibBootstrap.cogenBootstrap, overrides:_ *)
  }

  /**
    * Create a new Injector from a custom [[BootstrapContextModule]]
    *
    * @see [[DefaultBootstrapLocator]] and [[CglibBootstrap]] for a list available bootstrap modules
    */
  def apply(bootstrapBase: BootstrapContextModule, overrides: BootstrapModule*): Injector = {
    bootstrap(bootstrapBase, overrides.merge)
  }

  /**
    * Create a new injector inheriting configuration, hooks and object graph from a previous Injector's run
    *
    * @param parent Instances from parent [[Locator]] will be available as imports in new Injector's [[com.github.pshirshov.izumi.distage.model.Producer#produce produce]]
    */
  def inherit(parent: Locator): Injector = {
    new InjectorDefaultImpl(parent)
  }

  private[this] def bootstrap(
                               bootstrapBase: BootstrapContextModule = CglibBootstrap.cogenBootstrap,
                               overrides: BootstrapModule = BootstrapModule.empty,
                             ): Injector = {
    val bootstrapDefinition = bootstrapBase.overridenBy(overrides)
    val bootstrapLocator = new DefaultBootstrapLocator(bootstrapDefinition)
    inherit(bootstrapLocator)
  }

  object Standard extends InjectorBootstrap {
    def apply(): Injector = {
      bootstrap()
    }

    def apply(overrides: BootstrapModule*): Injector = {
      bootstrap(overrides = overrides.merge)
    }
  }

  object NoCogen extends InjectorBootstrap {
    def apply(): Injector = {
      bootstrap(bootstrapBase = DefaultBootstrapLocator.noReflectionBootstrap)
    }

    def apply(overrides: BootstrapModule*): Injector = {
      bootstrap(bootstrapBase = DefaultBootstrapLocator.noReflectionBootstrap, overrides = overrides.merge)
    }
  }

  object NoProxies extends InjectorBootstrap {
    def apply(): Injector = {
      bootstrap(bootstrapBase = DefaultBootstrapLocator.noProxiesBootstrap)
    }

    def apply(overrides: BootstrapModule*): Injector = {
      bootstrap(bootstrapBase = DefaultBootstrapLocator.noProxiesBootstrap, overrides = overrides.merge)
    }
  }

  trait InjectorBootstrap {
    def apply(): Injector

    def apply(overrides: BootstrapModule*): Injector
  }

}
