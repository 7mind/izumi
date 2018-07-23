package distage

import com.github.pshirshov.izumi.distage.InjectorDefaultImpl
import com.github.pshirshov.izumi.distage.bootstrap.{CglibBootstrap, DefaultBootstrapContext}
import com.github.pshirshov.izumi.distage.model.LocatorExtension

object Injector {
  def apply(): Injector = {
    bootstrap(CglibBootstrap.cogenBootstrap)
  }

  def apply(extensions: ModuleBase*): Injector = {
    bootstrap((CglibBootstrap.cogenBootstrap +: extensions).merge)
  }

  def noReflection(): Injector = {
    bootstrap(DefaultBootstrapContext.noReflectionBootstrap)
  }

  def noReflection(extensions: ModuleBase*): Injector = {
    bootstrap((DefaultBootstrapContext.noReflectionBootstrap +: extensions).merge)
  }

  def bootstrap(bootstrapBase: ModuleBase = CglibBootstrap.cogenBootstrap, bootstrapOverrides: ModuleBase = SimpleModuleDef.empty, locatorExtensions: Seq[LocatorExtension] = Seq()): Injector = {
    val bootstrapDefinition = bootstrapBase.overridenBy(bootstrapOverrides)
    val bootstrapLocator = new DefaultBootstrapContext(bootstrapDefinition)
    bootstrap(bootstrapLocator, locatorExtensions)
  }

  def bootstrap(parent: Locator, extensions: Seq[LocatorExtension]): Injector = {
    new InjectorDefaultImpl(parent.extend(extensions: _*))
  }
}
