package izumi.distage.docker

import izumi.fundamentals.platform.properties

/**
  * Java properties that control docker module defaults
  *
  * @see [[properties.DebugProperties]]
  */
object DockerSupportProperties extends properties.DebugProperties {
  final val `izumi.distage.docker.reuse` = StrProperty("izumi.distage.docker.reuse")
}
