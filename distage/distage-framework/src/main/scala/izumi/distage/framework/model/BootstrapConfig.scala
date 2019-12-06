package izumi.distage.framework.model

import distage.plugins.PluginConfig

final case class BootstrapConfig(
                                  pluginConfig: PluginConfig,
                                  bootstrapPluginConfig: Option[PluginConfig] = None,
                                )
object BootstrapConfig {
  def empty = BootstrapConfig(PluginConfig.empty, None)
}
