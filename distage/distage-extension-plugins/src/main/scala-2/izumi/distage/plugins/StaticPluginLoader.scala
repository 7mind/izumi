package izumi.distage.plugins

import izumi.distage.plugins.load.{LoadedPlugins, PluginLoaderDefaultImpl}
import izumi.fundamentals.platform.language.SourcePackageMaterializer.SourcePackageMaterializerMacro
import izumi.fundamentals.reflection.ReflectionUtil

import scala.reflect.macros.blackbox

/** Scan the specified package *at compile-time* for classes and objects that inherit [[PluginBase]]
  *
  * WARN: may interact badly with incremental compilation
  * WARN: will _not_ find plugins defined in the current module, only those defined in dependency modules
  *       (similarly to how you cannot call Scala macros defined in the current module)
  *
  * @see [[PluginConfig.compileTime]]
  */
object StaticPluginLoader {

  def scanCompileTime(pluginsPackage: String): List[PluginBase] = macro StaticPluginLoaderMacro.scanCompileTime

  object StaticPluginLoaderMacro {

    def scanCompileTimeConfig(c: blackbox.Context)(pluginsPackage: c.Expr[String]): c.Expr[PluginConfig] = {
      val plugins = scanCompileTime(c)(pluginsPackage)
      c.universe.reify {
        PluginConfig.const(plugins.splice)
      }
    }

    def scanCompileTimeConfigThisPkg(c: blackbox.Context): c.Expr[PluginConfig] = {
      val plugins = scanCompileTimeImpl(c)(SourcePackageMaterializerMacro.getSourcePackageString(c))
      c.universe.reify {
        PluginConfig.const(plugins.splice)
      }
    }

    def scanCompileTime(c: blackbox.Context)(pluginsPackage: c.Expr[String]): c.Expr[List[PluginBase]] = {
      val pluginPath = ReflectionUtil.getStringLiteral(c)(pluginsPackage.tree)

      scanCompileTimeImpl(c)(pluginPath)
    }

    def scanCompileTimeImpl(c: blackbox.Context)(pluginPath: String): c.Expr[List[PluginBase]] = {
      import c.universe.*

      val loadedPlugins = if (pluginPath == "") {
        LoadedPlugins.empty
      } else {
        new PluginLoaderDefaultImpl().load(PluginConfig.packages(Seq(pluginPath)))
      }

      val quoted: List[Tree] = instantiatePluginsInCode(c)(loadedPlugins.result)

      c.Expr[List[PluginBase]](q"$quoted")
    }

    def instantiatePluginsInCode(c: blackbox.Context)(loadedPlugins: Seq[PluginBase]): List[c.Tree] = {
      import c.universe.*
      val runtimeMirror = ru.runtimeMirror(this.getClass.getClassLoader)
      loadedPlugins.map {
        plugin =>
          val runtimeClassSymbol = runtimeMirror.classSymbol(plugin.getClass)
          if (runtimeClassSymbol.isModuleClass) {
            val obj = c.mirror.staticModule(runtimeClassSymbol.module.fullName)
            q"$obj"
          } else {
            val cls = c.mirror.staticClass(runtimeClassSymbol.fullName)
            q"new $cls"
          }
      }.toList
    }

  }

}
