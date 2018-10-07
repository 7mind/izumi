package distage

import com.github.pshirshov.izumi.distage.plugins.{load, merge}

package object plugins extends DistagePlugins {

  override type PluginBase = com.github.pshirshov.izumi.distage.plugins.PluginBase
  override val PluginBase: com.github.pshirshov.izumi.distage.plugins.PluginBase.type = com.github.pshirshov.izumi.distage.plugins.PluginBase

  override type PluginDef = com.github.pshirshov.izumi.distage.plugins.PluginDef

  override type PluginLoader = load.PluginLoader
  override type PluginPluginLoaderDefaultImpl = load.PluginLoaderDefaultImpl
  override val PluginPluginLoaderDefaultImpl: load.PluginLoaderDefaultImpl.type = load.PluginLoaderDefaultImpl
  override type PluginLoaderPredefImpl = load.PluginLoaderPredefImpl

  override type LoadedPlugins = com.github.pshirshov.izumi.distage.plugins.LoadedPlugins
  override val LoadedPlugins: com.github.pshirshov.izumi.distage.plugins.LoadedPlugins.type = com.github.pshirshov.izumi.distage.plugins.LoadedPlugins
  override type PluginMergeStrategy[T <: LoadedPlugins] = merge.PluginMergeStrategy[T]

  override type ConfigurablePluginMergeStrategy = merge.ConfigurablePluginMergeStrategy
  override val ConfigurablePluginMergeStrategy: merge.ConfigurablePluginMergeStrategy.type = merge.ConfigurablePluginMergeStrategy

  override val SimplePluginMergeStrategy: merge.SimplePluginMergeStrategy.type = merge.SimplePluginMergeStrategy
}
