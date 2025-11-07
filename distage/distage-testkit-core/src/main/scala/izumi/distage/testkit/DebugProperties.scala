package izumi.distage.testkit

import izumi.fundamentals.platform.properties

/**
  * Java properties that control debug logging and caching for distage-testkit
  *
  * @see [[properties.DebugProperties]]
  */
object DebugProperties extends properties.DebugProperties {
  /** Print debug messages, default: `false` */
  final val `izumi.distage.testkit.debug` = BoolProperty("izumi.distage.testkit.debug")

  /** Cache [[izumi.distage.testkit.model.TestEnvironment]], default: `true` */
  final val `izumi.distage.testkit.environment.cache` = BoolProperty("izumi.distage.testkit.environment.cache")

  /** Cache [[izumi.distage.modules.DefaultModule]] in testkit, default: `true` */
  final val `izumi.distage.testkit.defaultmodule.cache` = BoolProperty("izumi.distage.testkit.defaultmodule.cache")

  /** Force global test memoization on Scala.js (legacy mode), default: `false` */
  final val `izumi.distage.testkit.js.force.global.memoization` = BoolProperty("izumi.distage.testkit.js.force.global.memoization")
}
