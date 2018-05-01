package com.github.pshirshov.izumi.distage.plugins

import com.github.pshirshov.izumi.distage.model.definition.{AbstractModuleDef, PluginDef}
import com.github.pshirshov.izumi.functional.Value
import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner


case class PluginConfig(debug: Boolean, packagesEnabled: Seq[String], packagesDisabled: Seq[String])

private class ConfigApplicator(config: PluginConfig) {
  def debug(s: FastClasspathScanner): FastClasspathScanner = {
    if (config.debug) {
      s.verbose()
    } else {
      s
    }
  }
}


trait LoadedPlugins {
  def definition: AbstractModuleDef
}

case class JustLoadedPlugins(definition: AbstractModuleDef) extends LoadedPlugins

class PluginLoader(config: PluginConfig) {
  type PluginType = Class[_ <: PluginDef]

  import scala.collection.JavaConverters._

  def load(): Seq[PluginDef] = {
    val base = classOf[PluginDef]
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
  }

  def loadDefinition[R <: LoadedPlugins](mergeStrategy: PluginMergeStrategy[R]): R = {
    mergeStrategy.merge(load())
  }
}
