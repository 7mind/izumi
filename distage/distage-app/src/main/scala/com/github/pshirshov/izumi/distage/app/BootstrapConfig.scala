package com.github.pshirshov.izumi.distage.app

import com.github.pshirshov.izumi.distage.plugins.load.PluginLoaderDefaultImpl.PluginConfig

trait BootstrapConfig {
  def pluginConfig: PluginConfig
  def bootstrapPluginConfig: Option[PluginConfig]
}

object BootstrapConfig {
  def apply(pluginConfig: PluginConfig, bootstrapPluginConfig: Option[PluginConfig] = None): BootstrapConfig = {
    BootstrapConfigDefaultImpl(pluginConfig, bootstrapPluginConfig)
  }

  final case class BootstrapConfigDefaultImpl
  (
    pluginConfig: PluginConfig
  , bootstrapPluginConfig: Option[PluginConfig]
  ) extends BootstrapConfig
}
