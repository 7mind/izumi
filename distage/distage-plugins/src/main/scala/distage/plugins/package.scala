package distage

import com.github.pshirshov.izumi.distage.plugins.load

package object plugins extends DistagePlugins {

  override type PluginBase = com.github.pshirshov.izumi.distage.plugins.PluginBase
  override val PluginBase: com.github.pshirshov.izumi.distage.plugins.PluginBase.type = com.github.pshirshov.izumi.distage.plugins.PluginBase

  override type PluginDef = com.github.pshirshov.izumi.distage.plugins.PluginDef

  override type PluginLoader = load.PluginLoader
  override type PluginPluginLoaderDefaultImpl = load.PluginLoaderDefaultImpl
  override val PluginPluginLoaderDefaultImpl: load.PluginLoaderDefaultImpl.type = load.PluginLoaderDefaultImpl
  override type PluginLoaderPredefImpl = load.PluginLoaderPredefImpl

}
