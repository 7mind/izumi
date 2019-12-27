package izumi.distage.plugins

import izumi.fundamentals.platform.logging.DebugProperties

object DebugProperties extends DebugProperties {
  /** Scan classpath only once per test run */
  final val `izumi.distage.testkit.plugins.memoize` = "izumi.distage.testkit.plugins.memoize"
}
