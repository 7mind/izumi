package izumi.distage.plugins

import scala.compiletime.error

/** Scan the specified package *at compile-time* for classes and objects that inherit [[PluginBase]]
  *
  * WARN: may interact badly with incremental compilation
  * WARN: will _not_ find plugins defined in the current module, only those defined in dependency modules
  *       (similarly to how you cannot call Scala macros defined in the current module)
  *
  * @see [[PluginConfig.compileTime]]
  */
object StaticPluginLoader {

  inline def scanCompileTime(pluginsPackage: String): List[PluginBase] = error("unimplemented")

}
