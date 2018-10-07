package distage.plugins

import com.github.pshirshov.izumi.distage.plugins
import com.github.pshirshov.izumi.distage.plugins.load

trait DistagePlugins {

  type PluginBase = plugins.PluginBase
  val PluginBase: plugins.PluginBase.type = plugins.PluginBase

  type PluginDef = plugins.PluginDef

  type PluginLoader = load.PluginLoader
  type PluginPluginLoaderDefaultImpl = load.PluginLoaderDefaultImpl
  val PluginPluginLoaderDefaultImpl: load.PluginLoaderDefaultImpl.type = load.PluginLoaderDefaultImpl
  type PluginLoaderPredefImpl = load.PluginLoaderPredefImpl

  type LoadedPlugins = plugins.LoadedPlugins
  val LoadedPlugins: plugins.LoadedPlugins.type = plugins.LoadedPlugins
  type PluginMergeStrategy[T <: LoadedPlugins] = plugins.merge.PluginMergeStrategy[T]

  type ConfigurablePluginMergeStrategy = plugins.merge.ConfigurablePluginMergeStrategy
  val ConfigurablePluginMergeStrategy: plugins.merge.ConfigurablePluginMergeStrategy.type = plugins.merge.ConfigurablePluginMergeStrategy

  val SimplePluginMergeStrategy: plugins.merge.SimplePluginMergeStrategy.type = plugins.merge.SimplePluginMergeStrategy
}
