package izumi.distage.plugins.load

import izumi.distage.plugins.PluginBase

final class PluginLoaderPredefImpl(plugins: Seq[PluginBase]) extends PluginLoader {
  override def load(): Seq[PluginBase] = plugins
}
