package izumi.distage.testkit.services.dstest

import izumi.fundamentals.platform.logging.DebugProperties

object DebugProperties extends DebugProperties {
  /** Scan classpath only once per test run */
  final val `izumi.distage.testkit.environment.memoize` = "izumi.distage.testkit.environment.memoize"
}
