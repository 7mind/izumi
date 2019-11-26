package izumi.distage.plugins.load

import io.github.classgraph.ClassGraph
import izumi.distage.plugins.load.PluginLoader.PluginConfig
import izumi.distage.plugins.{PluginBase, PluginDef}
import izumi.functional.Value

import scala.jdk.CollectionConverters._
import scala.reflect.runtime.universe

class PluginLoaderDefaultImpl(pluginConfig: PluginConfig) extends PluginLoader {
  def load(): Seq[PluginBase] = {
    val base = classOf[PluginBase]
    val defClass = classOf[PluginDef]
    // Add package with PluginDef & PluginBase so that classgraph will resolve them
    val config = pluginConfig.copy(packagesEnabled = base.getPackage.getName +: defClass.getPackage.getName +: pluginConfig.packagesEnabled)

    val enabledPackages: Seq[String] = config.packagesEnabled.filterNot(config.packagesDisabled.contains)
    val disabledPackages: Seq[String] = config.packagesDisabled

    PluginLoaderDefaultImpl.load[PluginBase](base, Seq(defClass.getCanonicalName), enabledPackages, disabledPackages, config.debug)
  }
}

object PluginLoaderDefaultImpl {
  def load[T](base: Class[_], whitelist: Seq[String], enabledPackages: Seq[String], disabledPackages: Seq[String], debug: Boolean): Seq[T] = {
    val scanResult = Value(new ClassGraph())
      .map(_.whitelistPackages(enabledPackages: _*))
      .map(_.whitelistClasses(whitelist :+ base.getCanonicalName: _*))
      .map(_.blacklistPackages(disabledPackages: _*))
      .map(_.enableMethodInfo())
      .map(if (debug) _.verbose() else identity)
      .map(_.scan())
      .get

    try {
      val implementors = scanResult.getClassesImplementing(base.getCanonicalName)
      implementors
        .asScala
        .filterNot(_.isAbstract)
        .flatMap {
          classInfo =>
            val clz = classInfo.loadClass()
            val runtimeMirror = universe.runtimeMirror(clz.getClassLoader)
            val symbol = runtimeMirror.classSymbol(clz)

            if (symbol.isModuleClass) {
              Seq(runtimeMirror.reflectModule(symbol.thisPrefix.termSymbol.asModule).instance.asInstanceOf[T])
            } else {
              clz.getDeclaredConstructors.find(_.getParameterCount == 0).map(_.newInstance().asInstanceOf[T]).toSeq
            }
        }
        .toSeq // 2.13 compat
    } finally {
      scanResult.close()
    }
  }
}
