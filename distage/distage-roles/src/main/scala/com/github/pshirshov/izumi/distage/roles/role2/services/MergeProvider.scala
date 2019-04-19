package com.github.pshirshov.izumi.distage.roles.role2.services

import com.github.pshirshov.izumi.distage.roles.RolesInfo
import com.github.pshirshov.izumi.distage.roles.role2.services.PluginSource.AllLoadedPlugins
import distage.plugins.PluginMergeStrategy

trait MergeProvider {
  def mergeStrategy(plugins: AllLoadedPlugins, roles: RolesInfo): PluginMergeStrategy
}
