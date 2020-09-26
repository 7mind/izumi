package izumi.distage.gc

import distage.{AutoSetModule, Injector}
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

    Injector(Seq(AutoSetModule().register[AutoCloseable]) ++ more: _*)
  }

  def mkNoCglibInjector(): Injector[Identity] = {
    Injector.NoProxies(AutoSetModule().register[AutoCloseable])
  }
}
