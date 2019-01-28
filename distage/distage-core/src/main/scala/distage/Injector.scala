package distage

import com.github.pshirshov.izumi.distage.InjectorDefaultImpl
import com.github.pshirshov.izumi.distage.bootstrap.{CglibBootstrap, DefaultBootstrapLocator}
import com.github.pshirshov.izumi.distage.model.definition.BootstrapContextModule

object Injector {

  /**
    * Create a new Injector with garbage collection enabled
    *
    * GC will remove all bindings that aren’t transitive dependencies
    * of the chosen GC root keys from the plan - they will never be instantiated.
    *
    * @param roots Garbage Collection Root Keys
    * @param overrides Modules with overrides or additions to Injector's bootstrap environment,
    *                  They're used to extend the Injector, e.g. add ability to inject config values
    *                  or use GC
    */
  def apply(gcRoots: Set[DIKey], overrides: BootstrapModule*): Injector = {
    apply(gcRoots, CglibBootstrap.cogenBootstrap, overrides:_ *)
  }

  /**
    * Create a new Injector with a different [[BootstrapContextModule]]
    *
    * @see [[DefaultBootstrapLocator]] and [[CglibBootstrap]] for a list available bootstraps
    */
  def apply(gcRoots: Set[DIKey], bootstrapBase: BootstrapContextModule, overrides: BootstrapModule*): Injector = {
    new Gc(gcRoots, bootstrapBase)(overrides: _*)
  }

  /**
    * Create a new Injector without garbage collection
    * */
  def apply(overrides: BootstrapModule*): Injector = {
    bootstrap(overrides = overrides.merge)
  }

  def apply(bootstrapBase: BootstrapContextModule, overrides: BootstrapModule*): Injector = {
    bootstrap(bootstrapBase, overrides.overrideLeft)
  }

  def bootstrap(
                 bootstrapBase: BootstrapContextModule = CglibBootstrap.cogenBootstrap,
                 overrides: BootstrapModule = BootstrapModule.empty,
               ): Injector = {
    val bootstrapDefinition = bootstrapBase.overridenBy(overrides)
    val bootstrapLocator = new DefaultBootstrapLocator(bootstrapDefinition)
    inherit(bootstrapLocator)
  }

  /**
    * Create a new injector inheriting plugins, hooks and object graph from a previous Injector's run
    *
    * Instances from parent will be available as imports in the new Injector's [[com.github.pshirshov.izumi.distage.model.Producer#produce produce]]
    */
  def inherit(parent: Locator): Injector = {
    new InjectorDefaultImpl(parent)
  }

  @deprecated("Use Injector.NoCogen", "2018-10-29")
  def noReflection: Injector = {
    bootstrap(bootstrapBase = DefaultBootstrapLocator.noCogensBootstrap)
  }

  @deprecated("Use Injector.NoCogen", "2018-10-29")
  def noReflection(overrides: BootstrapModule*): Injector = {
    bootstrap(bootstrapBase = DefaultBootstrapLocator.noCogensBootstrap, overrides = overrides.merge)
  }
  /**
    * Create a new injector inheriting plugins, hooks and object graph from a previous Injector's run
    *
    * Instances from parent will be available as imports in the new Injector's [[com.github.pshirshov.izumi.distage.model.Producer#produce produce]]
    *
    * @deprecated Use Injector.inherit instead
    */
  @deprecated("Use Injector.inherit", "2018-10-30")
  def create(parent: Locator): Injector = {
    new InjectorDefaultImpl(parent)
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
      bootstrap(bootstrapBase = DefaultBootstrapLocator.noCogensBootstrap)
    }

    def apply(overrides: BootstrapModule*): Injector = {
      bootstrap(bootstrapBase = DefaultBootstrapLocator.noCogensBootstrap, overrides = overrides.merge)
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

  private[Injector] class Gc(roots: Set[DIKey], bootstrapBase: BootstrapContextModule) extends InjectorBootstrap {
    val gcModule = new TracingGCModule(roots)

    def apply(): Injector = {
      bootstrap(overrides = gcModule)
    }

    def apply(overrides: BootstrapModule*): Injector = {
      val allOverrides = overrides :+ gcModule
      bootstrap(bootstrapBase = bootstrapBase, overrides = allOverrides.merge)
    }
  }

  private[Injector] trait InjectorBootstrap {
    def apply(): Injector

    def apply(overrides: BootstrapModule*): Injector
  }

}
