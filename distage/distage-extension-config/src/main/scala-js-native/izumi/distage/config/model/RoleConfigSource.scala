package izumi.distage.config.model

sealed trait RoleConfigSource
object RoleConfigSource {
  final case class ConfigFile(file: String) extends RoleConfigSource

  case object ConfigDefault extends RoleConfigSource
}
