package izumi.distage.plugins

import izumi.distage.plugins.StaticPluginLoader.StaticPluginLoaderMacro
import scala.language.experimental.macros

trait PluginConfigStatic {
  /** Scan the specified package *at compile-time* for classes and objects that inherit [[PluginBase]]
    *
    * WARN: may interact badly with incremental compilation
    * WARN: will _not_ find plugins defined in the current module, only those defined in dependency modules
    *       (similarly to how you cannot call Scala macros defined in the current module)
    */
  def compileTime(pluginsPackage: String): PluginConfig = macro StaticPluginLoaderMacro.scanCompileTimeConfig

  /** Scan the the current source file's package *at compile-time* for classes and objects that inherit [[PluginBase]]
    *
    * WARN: may interact badly with incremental compilation
    * WARN: will _not_ find plugins defined in the current module, only those defined in dependency modules
    *       (similarly to how you cannot call Scala macros defined in the current module)
    */
  def compileTimeThisPkg: PluginConfig = macro StaticPluginLoaderMacro.scanCompileTimeConfigThisPkg
}
