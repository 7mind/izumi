package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.bootstrap.{CglibBootstrap, DefaultBootstrapContext}
import com.github.pshirshov.izumi.distage.model._
import com.github.pshirshov.izumi.distage.model.definition.{ModuleBase, SimpleModuleDef}


object Injectors {
  def bootstrap(extensions: LocatorExtension*): Injector = {
    bootstrap(SimpleModuleDef.empty, extensions :_*)
  }

  def bootstrap(overrides: ModuleBase, extensions: LocatorExtension*): Injector = {
    bootstrap(CglibBootstrap.cogenBootstrap, overrides, extensions :_*)
  }

  def bootstrap(base: ModuleBase, overrides: ModuleBase, extensions: LocatorExtension*): Injector = {
    val bootstrapDefinition = base.overridenBy(overrides)
    val bootstrapLocator = new DefaultBootstrapContext(bootstrapDefinition)
    create(bootstrapLocator, extensions: _*)
  }

  def create(parent: Locator, extensions: LocatorExtension*): Injector = {
    new InjectorDefaultImpl(parent.extend(extensions: _*))
  }
}
