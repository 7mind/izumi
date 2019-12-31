package izumi.distage.testkit.services.dstest

import izumi.fundamentals.platform.logging

/**
  * Java properties that control caching of distage-testkit classpath scans (when used)
  *
  * @see [[logging.DebugProperties]]
  */
object DebugProperties extends logging.DebugProperties {
  /** Cache created [[TestEnvironment]] */
  final val `izumi.distage.testkit.environment.cache` = "izumi.distage.testkit.environment.cache"
}
