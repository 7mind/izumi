package izumi.distage.injector

import distage.Injector

trait MkInjector {
  def mkInjector(): Injector = Injector.Standard()
  def mkStaticInjector(): Injector = Injector.NoReflection()
  def mkNoProxyInjector(): Injector = Injector.NoProxies()
}
