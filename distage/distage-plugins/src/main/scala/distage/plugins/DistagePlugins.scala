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

}
