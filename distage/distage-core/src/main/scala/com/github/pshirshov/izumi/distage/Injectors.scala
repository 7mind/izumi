package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.bootstrap.CglibBootstrap
import com.github.pshirshov.izumi.distage.model._
import com.github.pshirshov.izumi.distage.model.definition.ModuleBase
import distage.Injector

@deprecated("Use distage.Injector", "23 July 2018")
object Injectors {

  @deprecated("Use distage.Injector", "23 July 2018")
  def bootstrap(extensions: LocatorExtension*): Injector = {
    Injector.bootstrap(locatorExtensions = extensions)
  }

  @deprecated("Use distage.Injector", "23 July 2018")
  def bootstrap(overrides: ModuleBase, extensions: LocatorExtension*): Injector = {
    Injector.bootstrap(CglibBootstrap.cogenBootstrap, overrides, extensions)
  }

  @deprecated("Use distage.Injector", "23 July 2018")
  def bootstrap(base: ModuleBase, overrides: ModuleBase, extensions: LocatorExtension*): Injector = {
    Injector.bootstrap(base, overrides, extensions)
  }

  @deprecated("Use distage.Injector", "23 July 2018")
  def create(parent: Locator, extensions: LocatorExtension*): Injector = {
    Injector.bootstrap(parent, extensions)
  }
}
