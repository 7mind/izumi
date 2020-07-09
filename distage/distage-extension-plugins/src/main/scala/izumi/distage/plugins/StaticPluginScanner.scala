package izumi.distage.plugins

import scala.language.experimental.{macros => enableMacros}

object StaticPluginScanner {
  def staticallyAvailablePlugins(pluginsPackage: String): List[PluginBase] = macro StaticPluginScannerMacro.staticallyAvailablePlugins

}
