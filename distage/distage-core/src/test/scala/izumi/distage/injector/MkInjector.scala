package izumi.distage.injector

import distage.Injector
import izumi.fundamentals.platform.functional.Identity

trait MkInjector {
  def mkInjector(): Injector[Identity] = Injector.Standard()
  def mkNoProxiesInjector(): Injector[Identity] = Injector.NoProxies()
  def mkNoCyclesInjector(): Injector[Identity] = Injector.NoCycles()
}
