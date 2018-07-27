package com.github.pshirshov.izumi.distage.plugins.load

import com.github.pshirshov.izumi.distage.plugins.PluginBase
import com.github.pshirshov.izumi.distage.plugins.load.PluginLoaderDefaultImpl.{ConfigApplicator, PluginConfig}
import com.github.pshirshov.izumi.functional.Value
import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner
import scala.collection.JavaConverters._


class PluginLoaderDefaultImpl(pluginConfig: PluginConfig) extends PluginLoader {
  type PluginType = Class[_ <: PluginBase]

  def load(): Seq[PluginBase] = {
    val base = classOf[PluginBase]
    val config = pluginConfig.copy(packagesEnabled = pluginConfig.packagesEnabled :+ base.getPackage.getName)
    val configApplicator = new ConfigApplicator(config)

    val packages = config.packagesEnabled.filterNot(config.packagesDisabled.contains) ++
      config.packagesDisabled.map(p => s"-$p")

    val scanResult = Value(new FastClasspathScanner(packages: _*))
      .map(_.matchClassesImplementing(base, (_: PluginType) => ()))
      .map(configApplicator.debug)
      .map(_.scan())
      .get

    val pluginNames = scanResult.getNamesOfClassesImplementing(base).asScala
    val plugins = pluginNames.map(name => scanResult.getClassNameToClassInfo.get(name))

    plugins
      .map(_.getClassRef.asSubclass(base))
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
    def debug(s: FastClasspathScanner): FastClasspathScanner = {
      if (config.debug) {
        s.verbose()
      } else {
        s
      }
    }
  }
}
