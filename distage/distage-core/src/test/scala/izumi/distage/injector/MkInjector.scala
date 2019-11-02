package izumi.distage.injector

import distage.{BootstrapModule, Injector}

trait MkInjector {
  def mkInjector(): Injector = Injector.Standard()
  def mkStaticInjector(overrides: BootstrapModule*): Injector = Injector.NoCogen(overrides: _*)
  def mkInjectorWithProxy(): Injector = Injector.Standard()
}
