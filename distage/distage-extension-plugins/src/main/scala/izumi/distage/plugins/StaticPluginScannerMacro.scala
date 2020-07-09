package izumi.distage.plugins

import izumi.distage.plugins.load.PluginLoaderDefaultImpl
import izumi.fundamentals.reflection.ReflectionUtil

import scala.reflect.macros.blackbox
import scala.reflect.runtime.{universe => ru}
object StaticPluginScannerMacro {

  def staticallyAvailablePlugins(
    c: blackbox.Context
  )(pluginsPackage: c.Expr[String]
  ): c.Expr[List[PluginBase]] = {
    import c.universe._

    val pluginPath = ReflectionUtil.getStringLiteral(c)(pluginsPackage.tree)

    val loadedPlugins = if (pluginPath == "") {
      Seq.empty
    } else {
      new PluginLoaderDefaultImpl().load(PluginConfig.packages(Seq(pluginPath)))
    }

    val quoted: Seq[c.universe.Tree] = loadedPlugins.map {
      p =>
        val clazz = p.getClass
        val macroMirror: c.universe.Mirror = c.mirror
        val classSymbol = macroMirror.staticClass(clazz.getName)

        val runtimeMirror = ru.runtimeMirror(clazz.getClassLoader)
        val runtimeClassSymbol = runtimeMirror.classSymbol(clazz)

        if (runtimeClassSymbol.isModuleClass) {
          val tgt = macroMirror.staticModule(runtimeClassSymbol.asClass.module.fullName)
          q"$tgt"
        } else {
          q"new ${classSymbol.toType}"
        }
    }

    c.Expr[List[PluginBase]](q"${quoted.toList}")
  }

}
