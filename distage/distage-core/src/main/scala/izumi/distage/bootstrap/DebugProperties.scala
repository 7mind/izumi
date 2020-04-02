package izumi.distage.bootstrap

import izumi.fundamentals.platform.properties

object DebugProperties extends properties.DebugProperties {
  /** Print debug messages when planning Injector's own bootstrap environment */
  final val `izumi.distage.debug.bootstrap` = Property("izumi.distage.debug.bootstrap")
}
