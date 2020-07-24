package izumi.distage.testkit

import izumi.fundamentals.platform.properties

/**
  * Java properties that debug logging for distage-testkit and caching of distage-testkit classpath scans (when used)
  *
  * @see [[DebugProperties]]
  */
object DebugProperties extends properties.DebugProperties {
  /** Print debug messages, `false` by default */
  final val `izumi.distage.testkit.debug` = BoolProperty("izumi.distage.testkit.debug")

  /** Cache created [[izumi.distage.testkit.services.dstest.TestEnvironment]], `true` by default */
  final val `izumi.distage.testkit.environment.cache` = BoolProperty("izumi.distage.testkit.environment.cache")
}
