package com.github.pshirshov.izumi.distage.app

import com.github.pshirshov.izumi.distage.plugins.load.PluginLoaderDefaultImpl.PluginConfig

case class BootstrapConfig(
                            pluginConfig: PluginConfig,
                            bootstrapPluginConfig: Option[PluginConfig],
                          )

object BootstrapConfig {
  def apply(pluginConfig: PluginConfig, bootstrapPluginConfig: Option[PluginConfig] = None): BootstrapConfig = {
    new BootstrapConfig(pluginConfig, bootstrapPluginConfig)
  }
}
