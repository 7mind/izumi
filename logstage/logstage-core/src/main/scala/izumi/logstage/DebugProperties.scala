package izumi.logstage

import izumi.fundamentals.platform.properties

object DebugProperties extends properties.DebugProperties {
  final val `izumi.debug.macro.logstage` = Property("izumi.debug.macro.logstage")
  final val `izumi.logstage.rendering.colored` = Property("izumi.logstage.rendering.colored")
  final val `izumi.logstage.rendering.colored.forced` = Property("izumi.logstage.rendering.colored.forced")
}
