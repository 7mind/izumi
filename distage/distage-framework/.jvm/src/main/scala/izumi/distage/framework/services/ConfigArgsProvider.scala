package izumi.distage.framework.services

import izumi.distage.config.model.{GenericConfigSource, RoleConfig}
import izumi.distage.model.definition.Id
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

  open class Const(args0: ConfigLoader.Args) extends ConfigArgsProvider {
    override def args(): ConfigLoader.Args = args0
  }

  @nowarn("msg=Unused import")
  class Default(
    parameters: RawAppArgs,
    rolesInfo: RolesInfo,
    alwaysIncludeReferenceRoleConfigs: Boolean @Id("distage.roles.always-include-reference-role-configs"),
    alwaysIncludeReferenceCommonConfigs: Boolean @Id("distage.roles.always-include-reference-common-configs"),
    ignoreAllReferenceConfigs: Boolean @Id("distage.roles.ignore-all-reference-configs"),
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

      val ignoreAll = ignoreAllReferenceConfigs || parameters.globalParameters.hasFlag(RoleAppMain.Options.ignoreAllReferenceConfigs)
      val includeRole = !ignoreAll && alwaysIncludeReferenceRoleConfigs
      val includeCommon = !ignoreAll && alwaysIncludeReferenceCommonConfigs

      ConfigLoader.Args(maybeGlobalConfig, roleConfigs, includeRole, includeCommon, ignoreAll)
    }
  }
}
