package izumi.distage.plugins

import izumi.fundamentals.platform.properties

/**
  * Java properties that control the behavior of [[load.PluginLoader]]
  *
  * @see [[DebugProperties]]
  */
object DebugProperties extends properties.DebugProperties {
  /** Scan classpath only once per test run */
  final val `izumi.distage.plugins.cache` = BoolProperty("izumi.distage.plugins.cache")
}
