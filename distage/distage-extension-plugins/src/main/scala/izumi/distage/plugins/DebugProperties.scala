package izumi.distage.plugins

import izumi.fundamentals.platform.logging

object DebugProperties extends logging.DebugProperties {
  /** Scan classpath only once per test run */
  final val `izumi.distage.plugins.cache` = "izumi.distage.plugins.cache"
}
