package izumi.distage.plugins

import izumi.fundamentals.platform.language.SourcePackageMaterializer

import scala.compiletime.error

trait PluginConfigStatic {
  /** Scan the specified package *at compile-time* for classes and objects that inherit [[PluginBase]]
    *
    * WARN: may interact badly with incremental compilation
    * WARN: will _not_ find plugins defined in the current module, only those defined in dependency modules
    * (similarly to how you cannot call Scala macros defined in the current module)
    */
  inline def compileTime(inline pluginsPackage: String): PluginConfig = {
    val plugins = StaticPluginLoader.scanCompileTime(pluginsPackage)
    PluginConfig.const(plugins)
  }

  /** Scan the the current source file's package *at compile-time* for classes and objects that inherit [[PluginBase]]
    *
    * WARN: may interact badly with incremental compilation
    * WARN: will _not_ find plugins defined in the current module, only those defined in dependency modules
    * (similarly to how you cannot call Scala macros defined in the current module)
    */
  inline def compileTimeThisPkg: PluginConfig = compileTime(SourcePackageMaterializer.materializeSourcePackageString)
}
