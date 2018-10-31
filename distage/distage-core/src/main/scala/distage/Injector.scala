package distage

import com.github.pshirshov.izumi.distage.InjectorDefaultImpl
import com.github.pshirshov.izumi.distage.bootstrap.{CglibBootstrap, DefaultBootstrapContext}
import com.github.pshirshov.izumi.distage.model.definition.ModuleBase.ModuleDefSeqExt
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse


object Injector {

  /**
    * Create a new Injector with garbage collection enabled
    *
    * Alias for [[gc]]
    */
  def apply(gcRoots: Set[DIKey], overrides: BootstrapModule*): Injector = {
    gc(gcRoots, overrides: _*)
  }

  /**
    * Create a new Injector without garbage collection
    * */
  def apply(overrides: BootstrapModule*): Injector = {
    bootstrap(overrides = overrides.merge)
  }

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
  def gc(roots: Set[RuntimeDIUniverse.DIKey], overrides: BootstrapModule*): Injector = {
    new Gc(roots, CglibBootstrap.cogenBootstrap)(overrides:_ *)
  }

  def gcBootstrap(roots: Set[RuntimeDIUniverse.DIKey], bootstrapBase: BootstrapModule, overrides: BootstrapModule*): Injector = {
    new Gc(roots, bootstrapBase)(overrides: _*)
  }

  @deprecated("Use Injector.NoCogen", "2018-10-29")
  def noReflection: Injector = {
    bootstrap(bootstrapBase = DefaultBootstrapContext.noCogensBootstrap)
  }

  @deprecated("Use Injector.NoCogen", "2018-10-29")
  def noReflection(overrides: BootstrapModule*): Injector = {
    bootstrap(bootstrapBase = DefaultBootstrapContext.noCogensBootstrap, overrides = overrides.merge)
  }

  def bootstrap(
                 bootstrapBase: BootstrapModule = CglibBootstrap.cogenBootstrap,
                 overrides: BootstrapModule = BootstrapModule.empty,
               ): Injector = {
    val bootstrapDefinition = bootstrapBase.overridenBy(overrides)
    val bootstrapLocator = new DefaultBootstrapContext(bootstrapDefinition)
    inherit(bootstrapLocator)
  }

  /**
    * Create a new injector inheriting plugins, hooks and context from a previous Injector's run
    *
    * Instances from parent will be available as imports in the new Injector's [[com.github.pshirshov.izumi.distage.model.Producer#produce produce]]
    *
    * @deprecated Use Injector.inherit instead
    */
  @deprecated("Use Injector.inherit", "2018-10-30")
  def create(parent: Locator): Injector = {
    new InjectorDefaultImpl(parent)
  }

  /**
    * Create a new injector inheriting plugins, hooks and context from a previous Injector's run
    *
    * Instances from parent will be available as imports in the new Injector's [[com.github.pshirshov.izumi.distage.model.Producer#produce produce]]
    */
  def inherit(parent: Locator): Injector = {
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
      bootstrap(bootstrapBase = DefaultBootstrapContext.noCogensBootstrap)
    }

    def apply(overrides: BootstrapModule*): Injector = {
      bootstrap(bootstrapBase = DefaultBootstrapContext.noCogensBootstrap, overrides = overrides.merge)
    }
  }

  object NoProxies extends InjectorBootstrap {
    def apply(): Injector = {
      bootstrap(bootstrapBase = DefaultBootstrapContext.noProxiesBootstrap)
    }

    def apply(overrides: BootstrapModule*): Injector = {
      bootstrap(bootstrapBase = DefaultBootstrapContext.noProxiesBootstrap, overrides = overrides.merge)
    }
  }

  class Gc(roots: Set[RuntimeDIUniverse.DIKey], bootstrapBase: BootstrapModule) extends InjectorBootstrap {
    val gcModule = new TracingGCModule(roots)

    def apply(): Injector = {
      bootstrap(overrides = gcModule)
    }

    def apply(overrides: BootstrapModule*): Injector = {
      val allOverrides = overrides :+ gcModule
      bootstrap(bootstrapBase = bootstrapBase, overrides = allOverrides.merge)
    }
  }

  trait InjectorBootstrap {
    def apply(): Injector

    def apply(overrides: BootstrapModule*): Injector
}

}
