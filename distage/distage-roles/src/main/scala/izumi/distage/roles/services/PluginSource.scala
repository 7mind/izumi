package izumi.distage.roles.services

import distage.plugins.PluginBase
import izumi.distage.plugins.load.PluginLoader
import izumi.distage.roles.BootstrapConfig
import izumi.distage.roles.services.PluginSource.AllLoadedPlugins

trait PluginSource {
  def load(): AllLoadedPlugins
}

object PluginSource {
  case class AllLoadedPlugins(bootstrap: Seq[PluginBase], app: Seq[PluginBase])

  class Impl(
              config: BootstrapConfig,
            ) extends PluginSource {

    def load(): AllLoadedPlugins = {
      val bsl = mkBootstrapLoader().load()
      val l = mkLoader().load()
      AllLoadedPlugins(bsl, l)
    }

    protected def mkBootstrapLoader(): PluginLoader = {
      config.bootstrapPluginConfig.fold(PluginLoader.empty)(PluginLoader(_))
    }

    protected def mkLoader(): PluginLoader = {
      PluginLoader(config.pluginConfig)
    }
  }
}
