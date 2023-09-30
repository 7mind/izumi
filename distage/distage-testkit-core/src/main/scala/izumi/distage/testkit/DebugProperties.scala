package izumi.distage.testkit

import izumi.fundamentals.platform.properties

/**
  * Java properties that debug logging for distage-testkit and caching of distage-testkit classpath scans (when used)
  *
  * @see [[DebugProperties]]
  */
object DebugProperties extends properties.DebugProperties {
  /** Print debug messages, default: `false` */
  final val `izumi.distage.testkit.debug` = BoolProperty("izumi.distage.testkit.debug")

  /** Cache [[TestEnvironment]], default: `true` */
  final val `izumi.distage.testkit.environment.cache` = BoolProperty("izumi.distage.testkit.environment.cache")
}
