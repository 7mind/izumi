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

  def noReflection(): Injector = {
    bootstrap(DefaultBootstrapContext.noReflectionBootstrap)
  }

  def noReflection(overrides: ModuleBase*): Injector = {
    bootstrap(DefaultBootstrapContext.noReflectionBootstrap, overrides.merge)
  }

  def bootstrap(bootstrapBase: ModuleBase = CglibBootstrap.cogenBootstrap, overrides: ModuleBase = SimpleModuleDef.empty, locatorExtensions: Seq[LocatorExtension] = Seq()): Injector = {
    val bootstrapDefinition = bootstrapBase.overridenBy(overrides)
    val bootstrapLocator = new DefaultBootstrapContext(bootstrapDefinition)
    bootstrap(bootstrapLocator, locatorExtensions)
  }

  def bootstrap(parent: Locator, locatorExtensions: Seq[LocatorExtension]): Injector = {
    new InjectorDefaultImpl(parent.extend(locatorExtensions: _*))
  }
}
