package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.bootstrap.{CglibBootstrap, DefaultBootstrapContext}
import com.github.pshirshov.izumi.distage.model._
import com.github.pshirshov.izumi.distage.model.definition.{ModuleDef, TrivialModuleDef}


object Injectors {


  def bootstrap(extensions: LocatorExtension*): Injector = {
    bootstrap(TrivialModuleDef.empty, extensions :_*)
  }

  def bootstrap(overrides: ModuleDef, extensions: LocatorExtension*): Injector = {
    bootstrap(CglibBootstrap.cogenBootstrap, overrides, extensions :_*)
  }

  def bootstrap(base: ModuleDef, overrides: ModuleDef, extensions: LocatorExtension*): Injector = {
    val bootstrapDefinition = base.overridenBy(overrides)
    val bootstrapLocator = new DefaultBootstrapContext(bootstrapDefinition)
    create(bootstrapLocator, extensions: _*)
  }

  def create(parent: Locator, extensions: LocatorExtension*): Injector = {
    new InjectorDefaultImpl(parent.extend(extensions: _*))
  }
}
