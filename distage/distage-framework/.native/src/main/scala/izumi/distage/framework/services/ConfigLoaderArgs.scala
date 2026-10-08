package izumi.distage.framework.services

import izumi.distage.config.model.{RoleConfig, RoleConfigSource}
import izumi.distage.roles.RoleAppMain
import izumi.distage.roles.model.meta.RolesInfo
import izumi.fundamentals.platform.cli.model.RoleAppArgs

final case class ConfigLoaderArgs(
  global: Option[String],
  configs: List[RoleConfig],
)

object ConfigLoaderArgs {
  def fromRoleArgs(parameters: RoleAppArgs, rolesInfo: RolesInfo): ConfigLoaderArgs = {
    val specifiedRoleConfigs = parameters.roles.iterator
      .map(roleParams => roleParams.role -> roleParams.roleParameters.findValue(RoleAppMain.Options.configParam).map(_.value))
      .toMap

    val roleConfigs = rolesInfo.availableRoleNames.toList.map {
      roleName =>
        val source = specifiedRoleConfigs.get(roleName).flatten match {
          case Some(file) => RoleConfigSource.ConfigFile(file)
          case None => RoleConfigSource.ConfigDefault
        }
        RoleConfig(roleName, rolesInfo.requiredRoleNames.contains(roleName), source)
    }
    val global = parameters.globalParameters.findValue(RoleAppMain.Options.configParam).map(_.value)
    ConfigLoaderArgs(global, roleConfigs)
  }
}
