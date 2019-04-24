package com.github.pshirshov.izumi.distage.roles.services

import com.github.pshirshov.izumi.distage.model.definition.BindingTag
import com.github.pshirshov.izumi.distage.plugins.merge.ConfigurablePluginMergeStrategy.PluginMergeConfig
import com.github.pshirshov.izumi.distage.plugins.merge.{ConfigurablePluginMergeStrategy, PluginMergeStrategy}
import com.github.pshirshov.izumi.distage.roles.model.{BackendPluginTags, RolesInfo}
import com.github.pshirshov.izumi.distage.roles.services.PluginSource.AllLoadedPlugins
import com.github.pshirshov.izumi.distage.roles.RoleAppLauncher
import com.github.pshirshov.izumi.fundamentals.platform.cli.RoleAppArguments
import com.github.pshirshov.izumi.logstage.api.IzLogger

class MergeProviderImpl(logger: IzLogger, parameters: RoleAppArguments) extends MergeProvider {
  def mergeStrategy(plugins: AllLoadedPlugins, roles: RolesInfo): PluginMergeStrategy = {
    val unrequiredRoleTags = roles.unrequiredRoleNames.map(v => BindingTag.Expressions.Has(BindingTag(v)): BindingTag.Expressions.Expr)
    //
    val disabledTags = parameters.globalParameters.flags
      .find(f => RoleAppLauncher.useDummies.matches(f.name))
      .map(_ => filterProductionTags(true))
      .getOrElse(filterProductionTags(false))

    val allDisabledTags = BindingTag.Expressions.Or(Set(disabledTags) ++ unrequiredRoleTags)
    logger.trace(s"Raw disabled tags ${allDisabledTags -> "expression"}")
    logger.info(s"Disabled ${BindingTag.Expressions.TagDNF.toDNF(allDisabledTags) -> "tags"}")

    new ConfigurablePluginMergeStrategy(PluginMergeConfig(
      allDisabledTags
      , Set.empty
      , Set.empty
      , Map.empty
    ))
  }



  protected def filterProductionTags(useDummy: Boolean) : BindingTag.Expressions.Composite = {
    if (useDummy) {
      BindingTag.Expressions.all(BackendPluginTags.Production, BackendPluginTags.Storage)
    } else {
      BindingTag.Expressions.any(BackendPluginTags.Test, BackendPluginTags.Dummy)
    }
  }
}
