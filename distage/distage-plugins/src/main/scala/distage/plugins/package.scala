package distage

import izumi.distage.plugins.{load, merge}

package object plugins extends DistagePlugins {

  override type PluginBase = izumi.distage.plugins.PluginBase
  override val PluginBase: izumi.distage.plugins.PluginBase.type = izumi.distage.plugins.PluginBase

  override type PluginDef = izumi.distage.plugins.PluginDef

  override type PluginLoader = load.PluginLoader
  override val PluginLoader: load.PluginLoader.type = load.PluginLoader

  override type PluginLoaderDefaultImpl = load.PluginLoaderDefaultImpl

  override type PluginConfig = load.PluginLoader.PluginConfig
  override val PluginConfig: load.PluginLoader.PluginConfig.type = load.PluginLoader.PluginConfig

  override type PluginMergeStrategy = merge.PluginMergeStrategy

  override val SimplePluginMergeStrategy: merge.SimplePluginMergeStrategy.type = merge.SimplePluginMergeStrategy
}
