package izumi.distage.gc

import distage.Injector
import izumi.distage.planning.extensions.GraphDumpBootstrapModule
import izumi.fundamentals.platform.functional.Identity

trait MkGcInjector {
  def mkInjector(): Injector[Identity] = {
    val debug = false
    val more = if (debug) {
      Seq(GraphDumpBootstrapModule())
    } else {
      Seq.empty
    }

    Injector(bootstrapOverrides = more)
  }

  def mkNoProxiesInjector(): Injector[Identity] = {
    Injector.NoProxies()
  }
}
