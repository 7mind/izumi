package distage.plugins

import izumi.distage.plugins
import izumi.distage.plugins.{StaticPluginLoader, load}

trait DistagePlugins {

  type PluginBase = plugins.PluginBase
  val PluginBase: plugins.PluginBase.type = plugins.PluginBase

  type PluginDef[T] = plugins.PluginDef[T]

  type BootstrapPlugin = plugins.BootstrapPlugin
  val BootstrapPlugin: plugins.BootstrapPlugin.type = plugins.BootstrapPlugin

  type BootstrapPluginDef = plugins.BootstrapPluginDef

  type PluginLoader = load.PluginLoader
  val PluginLoader: load.PluginLoader.type = load.PluginLoader

  val StaticPlugingLoader: StaticPluginLoader.type = plugins.StaticPluginLoader

  type PluginLoaderDefaultImpl = load.PluginLoaderDefaultImpl
  val PluginLoaderDefaultImpl: load.PluginLoaderDefaultImpl.type = load.PluginLoaderDefaultImpl

  type PluginConfig = plugins.PluginConfig
  val PluginConfig: plugins.PluginConfig.type = plugins.PluginConfig

}
