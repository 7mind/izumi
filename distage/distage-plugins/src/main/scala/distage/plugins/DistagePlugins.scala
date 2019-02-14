package distage.plugins

import com.github.pshirshov.izumi.distage.plugins
import com.github.pshirshov.izumi.distage.plugins.load

trait DistagePlugins {

  type PluginBase = plugins.PluginBase
  val PluginBase: plugins.PluginBase.type = plugins.PluginBase

  type PluginDef = plugins.PluginDef

  type PluginLoader = load.PluginLoader
  val PluginLoader: load.PluginLoader.type = load.PluginLoader

  type PluginConfig = load.PluginLoaderDefaultImpl.PluginConfig
  val PluginConfig: load.PluginLoaderDefaultImpl.PluginConfig.type = load.PluginLoaderDefaultImpl.PluginConfig

  type PluginPluginLoaderDefaultImpl = load.PluginLoaderDefaultImpl
  val PluginPluginLoaderDefaultImpl: load.PluginLoaderDefaultImpl.type = load.PluginLoaderDefaultImpl
  type PluginLoaderPredefImpl = load.PluginLoaderPredefImpl

  type LoadedPlugins = plugins.LoadedPlugins
  val LoadedPlugins: plugins.LoadedPlugins.type = plugins.LoadedPlugins

  type PluginMergeStrategy = plugins.merge.PluginMergeStrategy

  type ConfigurablePluginMergeStrategy = plugins.merge.ConfigurablePluginMergeStrategy
  val ConfigurablePluginMergeStrategy: plugins.merge.ConfigurablePluginMergeStrategy.type = plugins.merge.ConfigurablePluginMergeStrategy

  type PluginMergeConfig = plugins.merge.ConfigurablePluginMergeStrategy.PluginMergeConfig
  val PluginMergeConfig: plugins.merge.ConfigurablePluginMergeStrategy.PluginMergeConfig.type = plugins.merge.ConfigurablePluginMergeStrategy.PluginMergeConfig

  val SimplePluginMergeStrategy: plugins.merge.SimplePluginMergeStrategy.type = plugins.merge.SimplePluginMergeStrategy
}
