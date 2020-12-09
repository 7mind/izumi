package izumi.distage.docker

import izumi.fundamentals.platform.properties

/**
  * Java properties that control docker module defaults
  *
  * @see [[izumi.fundamentals.platform.properties.DebugProperties]]
  */
object DebugProperties extends properties.DebugProperties {
  final val `izumi.distage.docker.reuse` = StrProperty("izumi.distage.docker.reuse")
}
