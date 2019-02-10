package distage

import com.github.pshirshov.izumi.distage.plugins.{load, merge}

package object plugins extends DistagePlugins {

  override type PluginBase = com.github.pshirshov.izumi.distage.plugins.PluginBase
  override val PluginBase: com.github.pshirshov.izumi.distage.plugins.PluginBase.type = com.github.pshirshov.izumi.distage.plugins.PluginBase

  override type PluginDef = com.github.pshirshov.izumi.distage.plugins.PluginDef

  override type PluginLoader = load.PluginLoader
  override val PluginLoader: load.PluginLoader.type = load.PluginLoader

  override type PluginConfig = load.PluginLoaderDefaultImpl.PluginConfig
  override val PluginConfig: load.PluginLoaderDefaultImpl.PluginConfig.type = load.PluginLoaderDefaultImpl.PluginConfig

  override type LoadedPlugins = com.github.pshirshov.izumi.distage.plugins.LoadedPlugins
  override val LoadedPlugins: com.github.pshirshov.izumi.distage.plugins.LoadedPlugins.type = com.github.pshirshov.izumi.distage.plugins.LoadedPlugins

  override type PluginMergeStrategy = merge.PluginMergeStrategy

  override type ConfigurablePluginMergeStrategy = merge.ConfigurablePluginMergeStrategy
  override val ConfigurablePluginMergeStrategy: merge.ConfigurablePluginMergeStrategy.type = merge.ConfigurablePluginMergeStrategy

  override type PluginMergeConfig = merge.ConfigurablePluginMergeStrategy.PluginMergeConfig
  override val PluginMergeConfig: merge.ConfigurablePluginMergeStrategy.PluginMergeConfig.type = merge.ConfigurablePluginMergeStrategy.PluginMergeConfig

  override val SimplePluginMergeStrategy: merge.SimplePluginMergeStrategy.type = merge.SimplePluginMergeStrategy
}
