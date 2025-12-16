package izumi.distage.config.model

final case class LoadedRoleConfigs(roleConfig: RoleConfig, loaded: Seq[ConfigLoadResult.Success])

final case class RoleConfig(role: String, active: Boolean, configSource: RoleConfigSource)
