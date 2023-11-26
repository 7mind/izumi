package izumi.distage.framework.services

import izumi.distage.config.model.{GenericConfigSource, RoleConfig}
import izumi.distage.roles.RoleAppMain
import izumi.distage.roles.model.meta.RolesInfo
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs

import java.io.File
import scala.annotation.nowarn

trait ConfigArgsProvider {
  def args(): ConfigLoader.Args
}

object ConfigArgsProvider {
  def const(args: ConfigLoader.Args): ConfigArgsProvider = new ConfigArgsProvider.Const(args)
  def empty: ConfigArgsProvider = ConfigArgsProvider.Empty

  open class Const(args0: ConfigLoader.Args) extends ConfigArgsProvider {
    override def args(): ConfigLoader.Args = args0
  }

  object Empty extends ConfigArgsProvider.Const(ConfigLoader.Args(None, List.empty))

  @nowarn("msg=Unused import")
  class Default(
    parameters: RawAppArgs,
    rolesInfo: RolesInfo,
  ) extends ConfigArgsProvider {
    override def args(): ConfigLoader.Args = {
      import scala.collection.compat.*

      val specifiedRoleConfigs: Map[String, Option[File]] = parameters.roles.iterator
        .map(roleParams => roleParams.role -> roleParams.roleParameters.findValue(RoleAppMain.Options.configParam).asFile)
        .toMap

      val roleConfigs = rolesInfo.availableRoleNames.toList.map {
        roleName =>
          val source = specifiedRoleConfigs.get(roleName).flatten match {
            case Some(value) =>
              GenericConfigSource.ConfigFile(value)
            case _ =>
              GenericConfigSource.ConfigDefault
          }
          RoleConfig(roleName, rolesInfo.requiredRoleNames.contains(roleName), source)
      }
      val maybeGlobalConfig = parameters.globalParameters.findValue(RoleAppMain.Options.configParam).asFile

      ConfigLoader.Args(maybeGlobalConfig, roleConfigs)
    }
  }
}
