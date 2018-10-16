package com.github.pshirshov.izumi.distage.plugins.load

import com.github.pshirshov.izumi.distage.plugins.PluginBase
import com.github.pshirshov.izumi.distage.plugins.load.PluginLoaderDefaultImpl.{ConfigApplicator, PluginConfig}
import com.github.pshirshov.izumi.functional.Value
import io.github.classgraph.{ClassGraph, ClassInfo}

import scala.collection.JavaConverters._


class PluginLoaderDefaultImpl(pluginConfig: PluginConfig) extends PluginLoader {
  type PluginType = Class[_ <: PluginBase]

  def load(): Seq[PluginBase] = {
    val base = classOf[PluginBase]
    val config = pluginConfig
    val configApplicator = new ConfigApplicator(config)

    val enabledPackages = config.packagesEnabled.filterNot(config.packagesDisabled.contains)
    val disabledPackages = config.packagesDisabled

    val scanResult = Value(new ClassGraph())
      .map(_.whitelistPackages(enabledPackages: _*))
      .map(_.blacklistPackages(disabledPackages: _*))
      .map(_.enableMethodInfo())
      .map(configApplicator.debug)
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

  private class ConfigApplicator(config: PluginConfig) {
    def debug(s: ClassGraph): ClassGraph = {
      if (config.debug) {
        s.verbose()
      } else {
        s
      }
    }
  }

}
