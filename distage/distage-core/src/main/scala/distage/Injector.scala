package distage

import com.github.pshirshov.izumi.distage.InjectorDefaultImpl
import com.github.pshirshov.izumi.distage.bootstrap.{CglibBootstrap, DefaultBootstrapContext}
import com.github.pshirshov.izumi.distage.model.LocatorExtension

object Injector {
  def apply(): Injector = {
    bootstrap()
  }

  def apply(overrides: ModuleBase*): Injector = {
    bootstrap(overrides = overrides.merge)
  }

  def noReflection: Injector = {
    bootstrap(DefaultBootstrapContext.noReflectionBootstrap)
  }

  def noReflection(overrides: ModuleBase*): Injector = {
    bootstrap(DefaultBootstrapContext.noReflectionBootstrap, overrides.merge)
  }

  def bootstrap(bootstrapBase: ModuleBase = CglibBootstrap.cogenBootstrap, overrides: ModuleBase = SimpleModuleDef.empty, locatorExtensions: Seq[LocatorExtension] = Seq()): Injector = {
    val bootstrapDefinition = bootstrapBase.overridenBy(overrides)
    val bootstrapLocator = new DefaultBootstrapContext(bootstrapDefinition)
    create(bootstrapLocator, locatorExtensions: _*)
  }

  /**
    * Create a new injector inheriting plugins, hooks and context from a previous Injector's run
    *
    * Instances from parent will be available as imports in new Injector's [[com.github.pshirshov.izumi.distage.model.Injector#produce]]
    */
  def create(parent: Locator, locatorExtensions: LocatorExtension*): Injector = {
    new InjectorDefaultImpl(parent.extend(locatorExtensions: _*))
  }
}
