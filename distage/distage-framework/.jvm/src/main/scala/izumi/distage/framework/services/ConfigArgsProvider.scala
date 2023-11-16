package izumi.distage.framework.services

import izumi.distage.roles.RoleAppMain
import izumi.distage.roles.model.meta.RolesInfo
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs

import java.io.File
import scala.annotation.nowarn

trait ConfigArgsProvider {
  def args(): ConfigLoader.Args
}

object ConfigArgsProvider {
  object Empty extends ConfigArgsProvider {
    override def args(): ConfigLoader.Args = ConfigLoader.Args(None, Map.empty)
  }

  @nowarn("msg=Unused import")
  class Default(
    parameters: RawAppArgs,
    rolesInfo: RolesInfo,
  ) extends ConfigArgsProvider {
    override def args(): ConfigLoader.Args = {
      import scala.collection.compat.*

      val emptyRoleConfigs = rolesInfo.availableRoleNames.map(_ -> None).toMap

      val maybeGlobalConfig = parameters.globalParameters.findValue(RoleAppMain.Options.configParam).asFile
      val specifiedRoleConfigs = parameters.roles.iterator
        .map(roleParams => roleParams.role -> roleParams.roleParameters.findValue(RoleAppMain.Options.configParam).asFile)
        .toMap

      //      ConfigLoader.Args(maybeGlobalConfig, (emptyRoleConfigs ++ specifiedRoleConfigs).view.toMap)
      val allConfigs: Map[String, Option[File]] = emptyRoleConfigs ++ specifiedRoleConfigs
      ConfigLoader.Args(maybeGlobalConfig, allConfigs.view.filterKeys(rolesInfo.requiredRoleNames).toMap)

    }

  }
}
