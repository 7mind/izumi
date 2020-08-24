package distage

import izumi.distage.plugins.{ForcedRecompilationToken, load}

package object plugins extends DistagePlugins {

  override type PluginBase = izumi.distage.plugins.PluginBase
  override val PluginBase: izumi.distage.plugins.PluginBase.type = izumi.distage.plugins.PluginBase

  override type PluginDef[T <: String] = izumi.distage.plugins.PluginDef[T]

  override type BootstrapPlugin = izumi.distage.plugins.BootstrapPlugin
  override val BootstrapPlugin: izumi.distage.plugins.BootstrapPlugin.type = izumi.distage.plugins.BootstrapPlugin

  override type BootstrapPluginDef = izumi.distage.plugins.BootstrapPluginDef

  override type PluginLoader = load.PluginLoader
  override val PluginLoader: load.PluginLoader.type = load.PluginLoader

  override type PluginLoaderDefaultImpl = load.PluginLoaderDefaultImpl
  override val PluginLoaderDefaultImpl: load.PluginLoaderDefaultImpl.type = load.PluginLoaderDefaultImpl

  override type PluginConfig = izumi.distage.plugins.PluginConfig
  override val PluginConfig: izumi.distage.plugins.PluginConfig.type = izumi.distage.plugins.PluginConfig

}
