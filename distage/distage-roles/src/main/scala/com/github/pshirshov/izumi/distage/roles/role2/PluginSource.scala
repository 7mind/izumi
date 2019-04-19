package com.github.pshirshov.izumi.distage.roles.role2

import com.github.pshirshov.izumi.distage.roles.role2.PluginSource.AllLoadedPlugins
import distage.plugins.PluginBase

trait PluginSource {
  def load(): AllLoadedPlugins

}

object PluginSource {
  case class AllLoadedPlugins(bootstrap: Seq[PluginBase], app: Seq[PluginBase])
}
