package izumi.distage.gc

import distage.{AutoSetModule, Injector}
import izumi.distage.planning.extensions.GraphDumpBootstrapModule

trait MkGcInjector {
  def mkInjector(): Injector = {
    val debug = false
    val more = if (debug) {
      Seq(GraphDumpBootstrapModule())
    } else {
      Seq.empty
    }

    Injector((Seq(AutoSetModule().register[AutoCloseable]) ++ more): _*)
  }

  def mkNoCglibInjector(): Injector = {
    Injector.NoProxies(AutoSetModule().register[AutoCloseable])
  }
}
