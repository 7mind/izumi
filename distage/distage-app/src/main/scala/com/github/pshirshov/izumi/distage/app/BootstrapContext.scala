package com.github.pshirshov.izumi.distage.app

import com.github.pshirshov.izumi.distage.plugins.load.PluginLoaderDefaultImpl.PluginConfig

trait BootstrapContext[CommandlineConfig] {
  def cliConfig: CommandlineConfig

  def pluginConfig: PluginConfig
}
