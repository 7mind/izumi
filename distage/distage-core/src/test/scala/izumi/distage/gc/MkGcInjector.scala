package izumi.distage.gc

import distage.Injector
import izumi.distage.planning.extensions.GraphDumpBootstrapModule
import izumi.functional.bio.Bifunctorized

trait MkGcInjector {
  def mkInjector(): Injector[Bifunctorized.IdentityBifunctorized] = {
    val debug = false
    val more = if (debug) {
      Seq(GraphDumpBootstrapModule())
    } else {
      Seq.empty
    }

    Injector[Bifunctorized.IdentityBifunctorized](bootstrapOverrides = more)
  }

  def mkNoProxiesInjector(): Injector[Bifunctorized.IdentityBifunctorized] = {
    Injector.NoProxies()
  }
}
