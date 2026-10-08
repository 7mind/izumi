package izumi.distage.plugins.load

class PluginLoaderDefaultImpl extends PluginLoaderClassgraphImpl

object PluginLoaderDefaultImpl {
  def apply(): PluginLoaderDefaultImpl = new PluginLoaderDefaultImpl()

  def withPackageCache(cache: PluginPackageCache): PluginLoaderDefaultImpl = new Owned(cache)

  private final class Owned(cache: PluginPackageCache) extends PluginLoaderDefaultImpl {
    override protected val packageCache: PluginPackageCache = cache
  }
}
