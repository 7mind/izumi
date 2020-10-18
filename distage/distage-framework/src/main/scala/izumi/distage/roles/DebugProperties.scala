package izumi.distage.roles

import izumi.fundamentals.platform.properties

object DebugProperties extends properties.DebugProperties {
  final val `izumi.distage.roles.activation.ignore-unknown` = BoolProperty("izumi.distage.roles.activation.ignore-unknown")
}
