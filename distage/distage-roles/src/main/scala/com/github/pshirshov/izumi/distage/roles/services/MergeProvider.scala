package com.github.pshirshov.izumi.distage.roles.services

import com.github.pshirshov.izumi.distage.roles.model.RolesInfo
import com.github.pshirshov.izumi.distage.roles.services.PluginSource.AllLoadedPlugins
import distage.plugins.PluginMergeStrategy

trait MergeProvider {
  def mergeStrategy(plugins: AllLoadedPlugins, roles: RolesInfo): PluginMergeStrategy
}
