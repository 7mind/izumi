package distage.plugins

import com.github.pshirshov.izumi.distage.plugins
import com.github.pshirshov.izumi.distage.plugins.load

trait DistagePlugins {

  type PluginBase = plugins.PluginBase
  val PluginBase: plugins.PluginBase.type = plugins.PluginBase

  type PluginDef = plugins.PluginDef

  type PluginLoader = load.PluginLoader
  val PluginLoader: load.PluginLoader.type = load.PluginLoader

  type PluginConfig = load.PluginLoader.PluginConfig
  val PluginConfig: load.PluginLoader.PluginConfig.type = load.PluginLoader.PluginConfig

  type PluginLoaderPredefImpl = load.PluginLoaderPredefImpl
  type PluginMergeStrategy = plugins.merge.PluginMergeStrategy

  val SimplePluginMergeStrategy: plugins.merge.SimplePluginMergeStrategy.type = plugins.merge.SimplePluginMergeStrategy
}
