package distage

import izumi.distage.plugins.load

package object plugins extends DistagePlugins {

  override type PluginBase = izumi.distage.plugins.PluginBase
  override val PluginBase: izumi.distage.plugins.PluginBase.type = izumi.distage.plugins.PluginBase

  override type PluginDef = izumi.distage.plugins.PluginDef

  override type PluginLoader = load.PluginLoader
  override val PluginLoader: load.PluginLoader.type = load.PluginLoader

  override type PluginLoaderDefaultImpl = load.PluginLoaderDefaultImpl

  override type PluginConfig = izumi.distage.plugins.PluginConfig
  override val PluginConfig: izumi.distage.plugins.PluginConfig.type = izumi.distage.plugins.PluginConfig

}
