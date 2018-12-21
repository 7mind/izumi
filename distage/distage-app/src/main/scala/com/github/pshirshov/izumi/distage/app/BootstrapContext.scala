package com.github.pshirshov.izumi.distage.app

import com.github.pshirshov.izumi.distage.plugins.load.PluginLoaderDefaultImpl.PluginConfig

trait BootstrapContext {
  def pluginConfig: PluginConfig
}

object BootstrapContext {
  case class BootstrapContextDefaultImpl
  (
    pluginConfig: PluginConfig
  ) extends BootstrapContext
}
