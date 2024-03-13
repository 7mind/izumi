package izumi.fundamentals.platform

object PlatformProperties extends properties.DebugProperties {
  final val `izumi.app.disable-terminal-colors` = BoolProperty("izumi.platform.disable-terminal-colors")
  final val `izumi.app.force-terminal-colors` = BoolProperty("izumi.platform.force-terminal-colors")
  final val `izumi.app.forced-headless` = BoolProperty("izumi.platform.forced-headless")
}
