package izumi.distage.plugins.load

import izumi.distage.plugins.PluginBase

object PluginLoaderNullImpl extends PluginLoader {
  override def load(): Seq[PluginBase] = Seq.empty
}
