package izumi.distage.framework.services

import izumi.distage.roles.RoleAppMain
import izumi.distage.roles.model.meta.RolesInfo
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs

trait ConfigArgsProvider {
  def args(): ConfigLoader.Args
}

object ConfigArgsProvider {
  object Empty extends ConfigArgsProvider {
    override def args(): ConfigLoader.Args = ConfigLoader.Args(None, Map.empty)
  }

  class Default(
    parameters: RawAppArgs,
    rolesInfo: RolesInfo,
  ) extends ConfigArgsProvider {
    override def args(): ConfigLoader.Args = {
      val emptyRoleConfigs = rolesInfo.availableRoleNames.map(_ -> None).toMap

      val maybeGlobalConfig = parameters.globalParameters.findValue(RoleAppMain.Options.configParam).asFile
      val specifiedRoleConfigs = parameters.roles.iterator
        .map(roleParams => roleParams.role -> roleParams.roleParameters.findValue(RoleAppMain.Options.configParam).asFile)
        .toMap

      //      ConfigLoader.Args(maybeGlobalConfig, (emptyRoleConfigs ++ specifiedRoleConfigs).view.toMap)
      ConfigLoader.Args(maybeGlobalConfig, (emptyRoleConfigs ++ specifiedRoleConfigs).view.filterKeys(rolesInfo.requiredRoleNames).toMap)

    }

  }
}
