package com.github.pshirshov.izumi.distage.roles.role2

import com.github.pshirshov.izumi.distage.plugins.merge.PluginMergeStrategy
import com.github.pshirshov.izumi.distage.roles.RolesInfo
import com.github.pshirshov.izumi.distage.roles.role2.PluginSource.AllLoadedPlugins

trait MergeProvider {
  def mergeStrategy(plugins: AllLoadedPlugins, roles: RolesInfo): PluginMergeStrategy
}
