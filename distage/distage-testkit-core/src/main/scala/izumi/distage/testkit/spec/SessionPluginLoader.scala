package izumi.distage.testkit.spec

import izumi.distage.plugins.PluginConfig
import izumi.distage.plugins.load.{LoadedPlugins, PluginLoader, PluginPackageCache}
import izumi.fundamentals.platform.cache.SyncCache

final class SessionPluginLoader(makeLoader: PluginPackageCache => PluginLoader) extends PluginLoader {
  private val cache = new SyncCache[PluginConfig, LoadedPlugins]
  private[distage] val packageCache: PluginPackageCache = new PluginPackageCache.Impl
  private val delegate = makeLoader(packageCache)

  override private[distage] def loadOwned(config: PluginConfig, owner: PluginPackageCache): LoadedPlugins = {
    require(owner eq packageCache, "A session plugin loader cannot belong to another session")
    load(config)
  }

  override def load(config: PluginConfig): LoadedPlugins = {
    val request = config.snapshot()
    if (request.cachePackages) {
      val uncached = request.cachePackages(false)
      cache.getOrCompute(uncached, delegate.load(request))
    } else {
      delegate.load(request)
    }
  }
}
