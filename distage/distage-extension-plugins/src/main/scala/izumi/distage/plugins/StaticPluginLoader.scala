package izumi.distage.plugins

import scala.language.experimental.macros
import izumi.distage.plugins.load.{LoadedPlugins, PluginLoaderDefaultImpl}
import izumi.fundamentals.reflection.ReflectionUtil

import scala.reflect.macros.blackbox
import scala.reflect.runtime.{universe => ru}

/** Scan the specified package *at compile-time* for classes and objects that inherit [[PluginBase]]
  *
  * WARN: may interact badly with incremental compilation
  * WARN: will _not_ find plugins defined in the current module, only those defined in dependency modules
  *       (similarly to how you cannot call Scala macros defined in the current module)
  *
  * @see [[PluginConfig.static]]
  */
object StaticPluginLoader {

  def staticallyAvailablePlugins(pluginsPackage: String): List[PluginBase] = macro StaticPluginLoaderMacro.staticallyAvailablePlugins

  object StaticPluginLoaderMacro {

    def staticallyAvailablePluginConfig(c: blackbox.Context)(pluginsPackage: c.Expr[String]): c.Expr[PluginConfig] = {
      val plugins = staticallyAvailablePlugins(c)(pluginsPackage)
      c.universe.reify {
        PluginConfig.const(plugins.splice)
      }
    }

    def staticallyAvailablePlugins(c: blackbox.Context)(pluginsPackage: c.Expr[String]): c.Expr[List[PluginBase]] = {
      import c.universe._

      val pluginPath = ReflectionUtil.getStringLiteral(c)(pluginsPackage.tree)

      val loadedPlugins = if (pluginPath == "") {
        LoadedPlugins.empty
      } else {
        new PluginLoaderDefaultImpl().load(PluginConfig.packages(Seq(pluginPath)))
      }

      val quoted: List[Tree] = instantiatePluginsInCode(c)(loadedPlugins.result)

      c.Expr[List[PluginBase]](q"$quoted")
    }

    def instantiatePluginsInCode(c: blackbox.Context)(loadedPlugins: Seq[PluginBase]): List[c.Tree] = {
      import c.universe._
      loadedPlugins.map {
        plugin =>
          val clazz = plugin.getClass
          val runtimeMirror = ru.runtimeMirror(clazz.getClassLoader)
          val runtimeClassSymbol = runtimeMirror.classSymbol(clazz)

          val macroMirror: Mirror = c.mirror

          if (runtimeClassSymbol.isModuleClass) {
            val tgt = macroMirror.staticModule(runtimeClassSymbol.module.fullName)
            q"$tgt"
          } else {
            val tgt = macroMirror.staticClass(runtimeClassSymbol.fullName)
            q"new $tgt"
          }
      }.toList
    }

  }

}
