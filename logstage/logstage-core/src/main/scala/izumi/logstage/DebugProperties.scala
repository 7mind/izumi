package izumi.logstage

import izumi.fundamentals.platform.properties

object DebugProperties extends properties.DebugProperties {
  final val `izumi.debug.macro.logstage` = BoolProperty("izumi.debug.macro.logstage")
  final val `izumi.logstage.rendering.colored` = BoolProperty("izumi.logstage.rendering.colored")
  final val `izumi.logstage.rendering.colored.forced` = BoolProperty("izumi.logstage.rendering.colored.forced")
  final val `izumi.logstage.routing.log-failures` = BoolProperty("izumi.logstage.routing.log-failures")
}
