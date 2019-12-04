package izumi.distage.injector

import distage.Injector

trait MkInjector {
  def mkInjector(): Injector = Injector.Standard()
  def mkNoReflectionInjector(): Injector = Injector.NoReflection()
  def mkNoProxyInjector(): Injector = Injector.NoProxies()
}
