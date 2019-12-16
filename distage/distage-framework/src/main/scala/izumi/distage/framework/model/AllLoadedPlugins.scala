package izumi.distage.framework.model

import distage.plugins.PluginBase

final case class AllLoadedPlugins(bootstrap: Seq[PluginBase], app: Seq[PluginBase])
