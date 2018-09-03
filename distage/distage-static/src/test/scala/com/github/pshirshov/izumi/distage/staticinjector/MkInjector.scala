package com.github.pshirshov.izumi.distage.staticinjector


import distage.{Injector, BootstrapModule}

trait MkInjector {

  def mkInjector(overrides: BootstrapModule*): Injector = Injector.noReflection(overrides: _*)

  def mkInjectorWithProxy(): Injector = Injector()

}
