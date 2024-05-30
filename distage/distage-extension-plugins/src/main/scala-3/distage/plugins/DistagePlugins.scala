package distage.plugins

import izumi.distage.plugins
import izumi.distage.plugins.{StaticPluginLoader, load}

trait DistagePlugins {

  type PluginBase = plugins.PluginBase
  val PluginBase: plugins.PluginBase.type = plugins.PluginBase

  // Because of Scala 3 bug https://github.com/scala/scala3/issues/19745
  // we can't use a type alias or export to alias PluginDef name anymore,
  // use the longer package izumi.distage.plugins.PluginDef instead.
//  type PluginDef[T] = plugins.PluginDef[T]

  type BootstrapPlugin = plugins.BootstrapPlugin
  val BootstrapPlugin: plugins.BootstrapPlugin.type = plugins.BootstrapPlugin

  // Because of Scala 3 bug https://github.com/scala/scala3/issues/19745
  // we can't use a type alias or export to alias BootstrapPluginDef name anymore,
  // use the longer package izumi.distage.plugins.BootstrapPluginDef instead.
//  type BootstrapPluginDef[T] = plugins.BootstrapPluginDef[T]

  type PluginLoader = load.PluginLoader
  val PluginLoader: load.PluginLoader.type = load.PluginLoader

  val StaticPlugingLoader: StaticPluginLoader.type = plugins.StaticPluginLoader

  type PluginLoaderDefaultImpl = load.PluginLoaderDefaultImpl
  val PluginLoaderDefaultImpl: load.PluginLoaderDefaultImpl.type = load.PluginLoaderDefaultImpl

  type PluginConfig = plugins.PluginConfig
  val PluginConfig: plugins.PluginConfig.type = plugins.PluginConfig

}
