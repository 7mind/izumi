package izumi.distage.testkit.spec.sessionplugins

import izumi.distage.plugins.PluginDef
import izumi.functional.lifecycle.Lifecycle
import izumi.fundamentals.platform.language.Quirks.*

import java.util.concurrent.atomic.AtomicInteger

final class SessionScannedPlugin extends PluginDef {
  val provisions = new AtomicInteger(0)
  val acquired = new AtomicInteger(0)
  val released = new AtomicInteger(0)
  make[SessionScannedValue].from {
    () =>
      provisions.incrementAndGet().discard()
      new SessionScannedValue
  }
  make[SessionMemoizedValue].fromResource {
    () =>
      Lifecycle.makeSimple {
        acquired.incrementAndGet().discard()
        new SessionMemoizedValue
      } {
        _ => released.incrementAndGet().discard()
      }
  }
}

final class SessionScannedValue
final class SessionMemoizedValue
