package izumi.distage.roles.services

import izumi.distage.plugins.load.PluginLoader
import izumi.distage.roles.BootstrapConfig
import izumi.distage.roles.services.PluginSource.AllLoadedPlugins

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
