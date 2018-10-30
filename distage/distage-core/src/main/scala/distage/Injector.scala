package distage

import com.github.pshirshov.izumi.distage.InjectorDefaultImpl
import com.github.pshirshov.izumi.distage.bootstrap.{CglibBootstrap, DefaultBootstrapContext}
import com.github.pshirshov.izumi.distage.model.definition.ModuleBase.ModuleDefSeqExt
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse


object Injector {

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


  def gc(roots: Set[RuntimeDIUniverse.DIKey], bootstrapBase: BootstrapModule = CglibBootstrap.cogenBootstrap): Gc = {
    new Gc(roots, bootstrapBase)
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

  @deprecated("Use Injector.Standard", "2018-10-29")
  def apply(): Injector = {
    bootstrap()
  }

  @deprecated("Use Injector.Standard", "2018-10-29")
  def apply(overrides: BootstrapModule*): Injector = {
    bootstrap(overrides = overrides.merge)
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
    create(bootstrapLocator)
  }

  /**
    * Create a new injector inheriting plugins, hooks and context from a previous Injector's run
    *
    * Instances from parent will be available as imports in the new Injector's [[com.github.pshirshov.izumi.distage.model.Producer#produce produce]]
    */
  def create(parent: Locator): Injector = {
    new InjectorDefaultImpl(parent)
  }
}
