package izumi.distage.plugins.load

import izumi.distage.plugins.PluginConfig

class PluginLoaderDefaultImpl extends PluginLoader {
  override def load(config: PluginConfig): LoadedPlugins = {
    LoadedPlugins(Seq.empty, config.merges, config.overrides)
  }
}

object PluginLoaderDefaultImpl {
  def apply(): PluginLoaderDefaultImpl = new PluginLoaderDefaultImpl()
}
