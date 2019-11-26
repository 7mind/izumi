package izumi.distage.staticinjector.plugins

import distage.plugins.PluginBase
import izumi.distage.staticinjector.plugins.macrodefs.StaticPluginCheckerMacro

import scala.language.experimental.macros

object StaticPluginChecker {
  def check[GcRoot <: PluginBase, R <: ModuleRequirements](activations: String): Unit = macro StaticPluginCheckerMacro.implDefault[GcRoot, R]
  def checkWithConfig[GcRoot <: PluginBase, R <: ModuleRequirements](activations: String, configFileRegex: String): Unit = macro StaticPluginCheckerMacro.implWithConfig[GcRoot, R]
  def checkWithPlugins[GcRoot <: PluginBase, R <: ModuleRequirements](pluginPath: String, activations: String): Unit = macro StaticPluginCheckerMacro.implWithPlugin[GcRoot, R]
  def checkWithPluginsConfig[GcRoot <: PluginBase, R <: ModuleRequirements](pluginPath: String, activations: String, configFileRegex: String): Unit = macro StaticPluginCheckerMacro.implWithPluginConfig[GcRoot, R]
}
