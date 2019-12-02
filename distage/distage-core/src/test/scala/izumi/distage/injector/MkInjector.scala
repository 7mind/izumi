package izumi.distage.injector

import distage.Injector

trait MkInjector {
  def mkInjector(): Injector = Injector.Standard()
  def mkStaticInjector(): Injector = Injector.NoCogen()
  def mkNoProxyInjector(): Injector = Injector.NoProxies()
}
