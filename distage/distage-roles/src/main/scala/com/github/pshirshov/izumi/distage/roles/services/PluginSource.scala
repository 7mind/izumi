package com.github.pshirshov.izumi.distage.roles.services

import com.github.pshirshov.izumi.distage.roles.services.PluginSource.AllLoadedPlugins
import distage.plugins.PluginBase

trait PluginSource {
  def load(): AllLoadedPlugins

}

object PluginSource {
  case class AllLoadedPlugins(bootstrap: Seq[PluginBase], app: Seq[PluginBase])
}
