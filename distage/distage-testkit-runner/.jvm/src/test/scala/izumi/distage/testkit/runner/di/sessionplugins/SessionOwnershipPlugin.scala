package izumi.distage.testkit.runner.di.sessionplugins

import izumi.distage.plugins.PluginDef
import izumi.functional.lifecycle.Lifecycle
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks.Discarder

import java.util.concurrent.atomic.AtomicInteger

final class SessionPluginState {
  val bodies = new AtomicInteger(0)
  val acquired = new AtomicInteger(0)
  val released = new AtomicInteger(0)
}

final class SessionPluginResource(val state: SessionPluginState)

final class SessionOwnershipPlugin extends PluginDef {
  private val state = new SessionPluginState
  make[SessionPluginResource].fromResource {
    () => Lifecycle.make[Identity, SessionPluginResource] {
      state.acquired.incrementAndGet().discard()
      new SessionPluginResource(state)
    } {
      _ => state.released.incrementAndGet().discard()
    }
  }
}
