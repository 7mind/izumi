package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.bootstrap.{CglibBootstrap, DefaultBootstrapContext}
import com.github.pshirshov.izumi.distage.model._
import com.github.pshirshov.izumi.distage.model.definition.{ModuleBase, SimpleModuleDef}

@deprecated("Use distage.Injector", "23 July 2018")
object Injectors {

  @deprecated("Use distage.Injector", "23 July 2018")
  def bootstrap(extensions: LocatorExtension*): Injector = {
    bootstrap(SimpleModuleDef.empty, extensions :_*)
  }

  @deprecated("Use distage.Injector", "23 July 2018")
  def bootstrap(overrides: ModuleBase, extensions: LocatorExtension*): Injector = {
    bootstrap(CglibBootstrap.cogenBootstrap, overrides, extensions :_*)
  }

  @deprecated("Use distage.Injector", "23 July 2018")
  def bootstrap(base: ModuleBase, overrides: ModuleBase, extensions: LocatorExtension*): Injector = {
    val bootstrapDefinition = base.overridenBy(overrides)
    val bootstrapLocator = new DefaultBootstrapContext(bootstrapDefinition)
    create(bootstrapLocator, extensions: _*)
  }

  @deprecated("Use distage.Injector", "23 July 2018")
  def create(parent: Locator, extensions: LocatorExtension*): Injector = {
    new InjectorDefaultImpl(parent.extend(extensions: _*))
  }
}
