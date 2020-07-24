package izumi.distage.plugins

import scala.language.experimental.macros

object StaticPluginScanner {
  def staticallyAvailablePlugins(pluginsPackage: String): List[PluginBase] = macro StaticPluginScannerMacro.staticallyAvailablePlugins
}
