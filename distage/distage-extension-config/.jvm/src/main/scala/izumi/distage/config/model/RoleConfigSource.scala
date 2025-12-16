package izumi.distage.config.model

import java.io.File

sealed trait RoleConfigSource
object RoleConfigSource {
  final case class ConfigFile(file: File) extends RoleConfigSource

  case object ConfigDefault extends RoleConfigSource
}
