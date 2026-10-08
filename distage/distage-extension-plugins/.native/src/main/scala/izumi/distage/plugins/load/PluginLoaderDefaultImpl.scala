package izumi.distage.plugins.load

import izumi.distage.plugins.PluginConfig
import izumi.distage.plugins.load.PluginLoaderDefaultImpl.RuntimePluginScanningNotSupportedOnScalaNative
import izumi.fundamentals.platform.language.Quirks.*
import izumi.fundamentals.platform.strings.IzString.toRichIterable

class PluginLoaderDefaultImpl extends PluginLoader {
  override def load(config: PluginConfig): LoadedPlugins = {
    if (config.packagesEnabled.nonEmpty) {
      throw new RuntimePluginScanningNotSupportedOnScalaNative(config.packagesEnabled)
    }
    LoadedPlugins(Nil, config.merges, config.overrides)
  }
}

object PluginLoaderDefaultImpl {
  def apply(): PluginLoaderDefaultImpl = new PluginLoaderDefaultImpl()

  def withPackageCache(cache: PluginPackageCache): PluginLoaderDefaultImpl = {
    cache.discard()
    new PluginLoaderDefaultImpl()
  }

  final class RuntimePluginScanningNotSupportedOnScalaNative(val packagesEnabled: Seq[String])
    extends RuntimeException(
      s"Runtime plugin scanning is not supported on Scala Native! Tried to scan packages at runtime:${packagesEnabled.niceList()}"
    )
}
