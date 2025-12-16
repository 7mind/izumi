package izumi.distage.framework.services

import izumi.distage.config.model.{RoleConfig, RoleConfigSource}
import izumi.distage.roles.RoleAppMain
import izumi.distage.roles.model.meta.RolesInfo
import izumi.fundamentals.platform.cli.model.RoleAppArgs

import java.io.File
import scala.annotation.nowarn

final case class ConfigLoaderArgs(
  global: Option[File],
  configs: List[RoleConfig],
)

object ConfigLoaderArgs {

  @nowarn("msg=[uU]nused import")
  def fromRoleArgs(
    parameters: RoleAppArgs,
    rolesInfo: RolesInfo,
  ): ConfigLoaderArgs = {
    import scala.collection.compat.*

    val specifiedRoleConfigs: Map[String, Option[File]] = parameters.roles.iterator
      .map(roleParams => roleParams.role -> roleParams.roleParameters.findValue(RoleAppMain.Options.configParam).asFile)
      .toMap

    val roleConfigs = rolesInfo.availableRoleNames.toList.map {
      roleName =>
        val source = specifiedRoleConfigs.get(roleName).flatten match {
          case Some(file) =>
            RoleConfigSource.ConfigFile(file)
          case _ =>
            RoleConfigSource.ConfigDefault
        }
        RoleConfig(roleName, rolesInfo.requiredRoleNames.contains(roleName), source)
    }
    val maybeGlobalConfig = parameters.globalParameters.findValue(RoleAppMain.Options.configParam).asFile

    ConfigLoaderArgs(maybeGlobalConfig, roleConfigs)
  }

}
