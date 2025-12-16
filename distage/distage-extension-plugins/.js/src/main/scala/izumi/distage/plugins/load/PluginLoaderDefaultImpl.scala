package izumi.distage.plugins.load

import izumi.distage.plugins.PluginConfig
import izumi.distage.plugins.load.PluginLoaderDefaultImpl.RuntimePluginScanningNotSupportedOnScalajs
import izumi.fundamentals.platform.strings.IzString.toRichIterable

class PluginLoaderDefaultImpl extends PluginLoader {
  override def load(config: PluginConfig): LoadedPlugins = {
    if (config.packagesEnabled.nonEmpty) {
      throw new RuntimePluginScanningNotSupportedOnScalajs(config.packagesEnabled)
    }
    LoadedPlugins(Nil, config.merges, config.overrides)
  }
}

object PluginLoaderDefaultImpl {
  def apply(): PluginLoaderDefaultImpl = new PluginLoaderDefaultImpl()

  final class RuntimePluginScanningNotSupportedOnScalajs(val packagesEnabled: Seq[String])
    extends RuntimeException(
      s"Runtime plugin scanning is not supported on Scala.js! Tried to scan packages at runtime:${packagesEnabled.niceList()}"
    )
}
