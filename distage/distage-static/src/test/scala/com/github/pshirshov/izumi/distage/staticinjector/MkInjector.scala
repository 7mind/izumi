package com.github.pshirshov.izumi.distage.staticinjector

import distage.{Injector, ModuleBase}

trait MkInjector {

  def mkInjector(overrides: ModuleBase*): Injector = Injector.noReflection(overrides: _*)

  def mkInjectorWithProxy(): Injector = Injector()

}
