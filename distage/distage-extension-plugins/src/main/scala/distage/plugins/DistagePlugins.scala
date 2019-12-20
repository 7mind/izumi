package distage.plugins

import izumi.distage.plugins
import izumi.distage.plugins.load

trait DistagePlugins {

  type PluginBase = plugins.PluginBase
  val PluginBase: plugins.PluginBase.type = plugins.PluginBase

  type PluginDef = plugins.PluginDef

  type PluginLoader = load.PluginLoader
  val PluginLoader: load.PluginLoader.type = load.PluginLoader

  type PluginLoaderDefaultImpl = load.PluginLoaderDefaultImpl

  type PluginConfig = izumi.distage.plugins.PluginConfig
  val PluginConfig: izumi.distage.plugins.PluginConfig.type = izumi.distage.plugins.PluginConfig
}
