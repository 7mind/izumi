package com.github.pshirshov.izumi.distage.roles.role2.services

import com.github.pshirshov.izumi.distage.app.BootstrapConfig
import com.github.pshirshov.izumi.distage.plugins.load.PluginLoader
import com.github.pshirshov.izumi.distage.roles.role2.services.PluginSource.AllLoadedPlugins

class PluginSourceImpl(config: BootstrapConfig) extends PluginSource {
  def load(): AllLoadedPlugins = {
    val bsl = mkBootstrapLoader().load()
    val l = mkLoader().load()
    AllLoadedPlugins(bsl, l)
  }
  def mkBootstrapLoader(): PluginLoader = {
    config.bootstrapPluginConfig.fold(PluginLoader.empty)(PluginLoader(_))
  }

  def mkLoader(): PluginLoader = {
    PluginLoader(config.pluginConfig)
  }
}
