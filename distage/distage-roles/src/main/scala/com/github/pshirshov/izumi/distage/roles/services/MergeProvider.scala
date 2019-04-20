package com.github.pshirshov.izumi.distage.roles.services

import com.github.pshirshov.izumi.distage.roles.RolesInfo
import com.github.pshirshov.izumi.distage.roles.services.PluginSource.AllLoadedPlugins
import distage.plugins.PluginMergeStrategy

trait MergeProvider {
  def mergeStrategy(plugins: AllLoadedPlugins, roles: RolesInfo): PluginMergeStrategy
}
