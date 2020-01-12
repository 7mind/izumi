package izumi.distage.injector

import distage.Injector

trait MkInjector {
  def mkInjector(): Injector = Injector.Standard()
  def mkNoCglibInjector(): Injector = Injector.NoProxies()
  def mkNoCyclesInjector(): Injector = Injector.NoCycles()
}
