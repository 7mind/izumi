package com.github.pshirshov.izumi.distage.app

import com.github.pshirshov.izumi.distage.plugins.load.PluginLoaderDefaultImpl.PluginConfig

case class BootstrapContextDefaultImpl[CommandlineConfig]
(
  cliConfig: CommandlineConfig
  , pluginConfig: PluginConfig
) extends BootstrapContext[CommandlineConfig]
