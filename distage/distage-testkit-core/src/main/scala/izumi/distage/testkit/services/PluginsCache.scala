package izumi.distage.testkit.services

import izumi.distage.framework.model.{AllLoadedPlugins, BootstrapConfig, PluginSource}

object PluginsCache {
  final case class CacheKey(config: BootstrapConfig)
  final case class CacheValue(plugins: AllLoadedPlugins)

  // sbt in nofork mode runs each module in it's own classloader thus we have separate cache per module per run
  object Instance extends SyncCache[CacheKey, CacheValue]

  def cachePluginSource(pluginSource: PluginSource): PluginSource = {
    pluginSource.replaceLoaders {
      case p@PluginSource.Load(_, _, Some(bootstrapConfig)) =>
        PluginSource(Instance.getOrCompute(CacheKey(bootstrapConfig), CacheValue(p.load())).plugins)
      case p => p
    }
  }
}
