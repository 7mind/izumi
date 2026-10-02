package izumi.distage.testkit.spec.sessionplugins

import izumi.distage.plugins.PluginDef
import izumi.fundamentals.platform.language.Quirks.*

import java.util.concurrent.atomic.AtomicInteger

final class SessionScannedPlugin extends PluginDef {
  val provisions = new AtomicInteger(0)
  make[SessionScannedValue].from {
    () =>
      provisions.incrementAndGet().discard()
      new SessionScannedValue
  }
}

final class SessionScannedValue
