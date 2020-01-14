package izumi.distage.testkit

import izumi.fundamentals.platform.logging

/**
  * Java properties that debug logging for distage-testkit and caching of distage-testkit classpath scans (when used)
  *
  * @see [[logging.DebugProperties]]
  */
object DebugProperties extends logging.DebugProperties {
  /** Print debug messages */
  final val `izumi.distage.testkit.debug` = "izumi.distage.testkit.debug"

  /** Cache created [[izumi.distage.testkit.services.dstest.TestEnvironment]] */
  final val `izumi.distage.testkit.environment.cache` = "izumi.distage.testkit.environment.cache"
}
