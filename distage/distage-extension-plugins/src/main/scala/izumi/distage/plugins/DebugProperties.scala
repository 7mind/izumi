package izumi.distage.plugins

import izumi.fundamentals.platform.properties.DebugProperties

/**
  * Java properties that control behavior of [[load.PluginLoader]]
  *
  * @see [[DebugProperties]]
  */
object DebugProperties extends DebugProperties {
  /** Scan classpath only once per test run */
  final val `izumi.distage.plugins.cache` = "izumi.distage.plugins.cache"
}
