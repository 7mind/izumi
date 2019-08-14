package com.github.pshirshov.izumi.distage.roles

import distage.plugins.PluginConfig

case class BootstrapConfig(
                            pluginConfig: PluginConfig,
                            bootstrapPluginConfig: Option[PluginConfig],
                          )

object BootstrapConfig {
  def apply(pluginConfig: PluginConfig, bootstrapPluginConfig: Option[PluginConfig] = None): BootstrapConfig = {
    new BootstrapConfig(pluginConfig, bootstrapPluginConfig)
  }
}
