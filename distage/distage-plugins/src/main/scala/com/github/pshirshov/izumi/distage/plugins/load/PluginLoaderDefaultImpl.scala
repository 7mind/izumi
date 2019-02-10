package com.github.pshirshov.izumi.distage.plugins.load

import com.github.pshirshov.izumi.distage.plugins.load.PluginLoaderDefaultImpl.PluginConfig
import com.github.pshirshov.izumi.distage.plugins.{PluginBase, PluginDef}
import com.github.pshirshov.izumi.functional.Value
import io.github.classgraph.{ClassGraph, ClassInfo}

import scala.collection.JavaConverters._

class PluginLoaderDefaultImpl(pluginConfig: PluginConfig) extends PluginLoader {
  def load(): Seq[PluginBase] = {
    val base = classOf[PluginBase]
    val defClass = classOf[PluginDef]
    // Add package with PluginDef & PluginBase so that classgraph will resolve them
    val config = pluginConfig.copy(packagesEnabled =  base.getPackage.getName +: defClass.getPackage.getName +: pluginConfig.packagesEnabled)

    val enabledPackages = config.packagesEnabled.filterNot(config.packagesDisabled.contains)
    val disabledPackages = config.packagesDisabled

    val scanResult = Value(new ClassGraph())
      .map(_.whitelistPackages(enabledPackages: _*))
      .map(_.whitelistClasses(base.getCanonicalName, defClass.getCanonicalName))
      .map(_.blacklistPackages(disabledPackages: _*))
      .map(_.enableMethodInfo())
      .map(if (pluginConfig.debug) _.verbose() else identity)
      .map(_.scan())
      .get

    val implementors = scanResult.getClassesImplementing(base.getCanonicalName)
    val plugins = implementors.filter {
      case classInfo: ClassInfo =>
        classInfo.getConstructorInfo.asScala.exists(_.getParameterInfo.isEmpty)
    }

    val pluginClasses = plugins.loadClasses(base).asScala

    pluginClasses
      .map(_.getDeclaredConstructor().newInstance())
      .toSeq // 2.13 compat
  }
}

object PluginLoaderDefaultImpl {

  final case class PluginConfig
  (
    debug: Boolean
  , packagesEnabled: Seq[String]
  , packagesDisabled: Seq[String]
  )

}
