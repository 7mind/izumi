package izumi.distage.injector

import distage.Injector
import izumi.functional.bio.Bifunctorized

trait MkInjector {
  def mkInjector(): Injector[Bifunctorized.IdentityBifunctorized] = Injector.Standard()
  def mkNoProxiesInjector(): Injector[Bifunctorized.IdentityBifunctorized] = Injector.NoProxies()
  def mkNoCyclesInjector(): Injector[Bifunctorized.IdentityBifunctorized] = Injector.NoCycles()
}
