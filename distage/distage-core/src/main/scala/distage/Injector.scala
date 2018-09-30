package distage

import com.github.pshirshov.izumi.distage.InjectorDefaultImpl
import com.github.pshirshov.izumi.distage.bootstrap.{CglibBootstrap, DefaultBootstrapContext}
import com.github.pshirshov.izumi.distage.model.LocatorExtension
import com.github.pshirshov.izumi.distage.model.definition.ModuleBase.ModuleDefSeqExt

object Injector {
  def apply(): Injector = {
    bootstrap()
  }

  def apply(overrides: BootstrapModule*): Injector = {
    bootstrap(overrides = new ModuleDefSeqExt[BootstrapModule, BootstrapModule](overrides).merge)
  }

  def noReflection: Injector = {
    bootstrap(DefaultBootstrapContext.noReflectionBootstrap)
  }

  def noReflection(overrides: BootstrapModule*): Injector = {
    bootstrap(DefaultBootstrapContext.noReflectionBootstrap, overrides.merge)
  }

  def bootstrap(
                 bootstrapBase: BootstrapModule = CglibBootstrap.cogenBootstrap
                 , overrides: BootstrapModule = BootstrapModule.empty
                 , locatorExtensions: Seq[LocatorExtension] = Seq()
               ): Injector = {
    val bootstrapDefinition = bootstrapBase.overridenBy(overrides)
    val bootstrapLocator = new DefaultBootstrapContext(bootstrapDefinition)
    create(bootstrapLocator, locatorExtensions: _*)
  }

  /**
    * Create a new injector inheriting plugins, hooks and context from a previous Injector's run
    *
    * Instances from parent will be available as imports in the new Injector's [[com.github.pshirshov.izumi.distage.model.Producer#produce produce]]
    */
  def create(parent: Locator, locatorExtensions: LocatorExtension*): Injector = {
    new InjectorDefaultImpl(parent.extend(locatorExtensions: _*))
  }
}
