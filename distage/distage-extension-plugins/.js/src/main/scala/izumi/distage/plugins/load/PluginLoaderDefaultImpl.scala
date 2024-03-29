package izumi.distage.plugins.load

import izumi.distage.plugins.PluginConfig

class PluginLoaderDefaultImpl extends PluginLoader {
  /** Will disable scanning if no packages are specified (add `"_root_"` package if you want to scan everything) */
  override def load(config: PluginConfig): LoadedPlugins = {
    LoadedPlugins(Nil, config.merges, config.overrides)
  }
}

object PluginLoaderDefaultImpl {
  def apply(): PluginLoaderDefaultImpl = new PluginLoaderDefaultImpl()
}
