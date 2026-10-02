package izumi.distage.testkit.spec

import izumi.distage.plugins.PluginConfig
import izumi.distage.plugins.load.{LoadedPlugins, PluginLoader}
import izumi.fundamentals.platform.cache.SyncCache

final class SessionPluginLoader(delegate: PluginLoader) extends PluginLoader {
  private val cache = new SyncCache[PluginConfig, LoadedPlugins]

  override def load(config: PluginConfig): LoadedPlugins = {
    if (config.cachePackages) {
      val uncached = config.cachePackages(false)
      cache.getOrCompute(uncached, delegate.load(uncached))
    } else {
      delegate.load(config)
    }
  }
}
