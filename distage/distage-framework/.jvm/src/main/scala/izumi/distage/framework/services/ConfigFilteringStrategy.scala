package izumi.distage.framework.services

import izumi.distage.config.model.{ConfigLoadResult, LoadedRoleConfigs}
import izumi.distage.model.definition.Id
import izumi.distage.roles.RoleAppMain
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs

trait ConfigFilteringStrategy {
  def filterSharedConfigs(shared: List[ConfigLoadResult.Success]): List[ConfigLoadResult.Success]
  def filterRoleConfigs(role: List[LoadedRoleConfigs]): List[LoadedRoleConfigs]
}

object ConfigFilteringStrategy {
  def apply(
    filterShared: List[ConfigLoadResult.Success] => List[ConfigLoadResult.Success],
    filterRole: List[LoadedRoleConfigs] => List[LoadedRoleConfigs],
  ): ConfigFilteringStrategy = {
    new ConfigFilteringStrategy {
      override def filterSharedConfigs(shared: List[ConfigLoadResult.Success]): List[ConfigLoadResult.Success] = filterShared(shared)
      override def filterRoleConfigs(role: List[LoadedRoleConfigs]): List[LoadedRoleConfigs] = filterRole(role)
    }
  }

  class Default(
    parameters: RawAppArgs,
    alwaysIncludeReferenceRoleConfigs: Boolean @Id("distage.roles.always-include-reference-role-configs"),
    alwaysIncludeReferenceCommonConfigs: Boolean @Id("distage.roles.always-include-reference-common-configs"),
    ignoreAllReferenceConfigs: Boolean @Id("distage.roles.ignore-all-reference-configs"),
  ) extends ConfigFilteringStrategy.Raw(
      alwaysIncludeReferenceRoleConfigs = alwaysIncludeReferenceRoleConfigs,
      alwaysIncludeReferenceCommonConfigs = alwaysIncludeReferenceCommonConfigs,
      ignoreAll = ignoreAllReferenceConfigs || parameters.globalParameters.hasFlag(RoleAppMain.Options.ignoreAllReferenceConfigs),
    )

  open class Raw(
    alwaysIncludeReferenceRoleConfigs: Boolean,
    alwaysIncludeReferenceCommonConfigs: Boolean,
    protected val ignoreAll: Boolean,
  ) extends ConfigFilteringStrategy {

    protected val includeRoleReferences: Boolean = !ignoreAll && alwaysIncludeReferenceRoleConfigs
    protected val includeCommonReferences: Boolean = !ignoreAll && alwaysIncludeReferenceCommonConfigs

    override def filterSharedConfigs(shared: List[ConfigLoadResult.Success]): List[ConfigLoadResult.Success] = {
      if (ignoreAll || (shared.exists(_.isExplicit) && !includeCommonReferences)) {
        shared.filter(_.isExplicit)
      } else {
        shared
      }
    }

    override def filterRoleConfigs(role: List[LoadedRoleConfigs]): List[LoadedRoleConfigs] = {
      role.map {
        roleConfigs =>
          if (ignoreAll || (roleConfigs.loaded.exists(_.isExplicit) && !includeRoleReferences)) {
            roleConfigs.copy(loaded = roleConfigs.loaded.filter(_.isExplicit))
          } else {
            roleConfigs
          }
      }
    }

  }

}
