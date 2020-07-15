package izumi.distage.staticinjector.plugins

import distage.ModuleBase
import izumi.distage.staticinjector.plugins.macros.StaticPluginCheckerMacro

import scala.language.experimental.{macros => enableMacros}

object StaticPluginChecker {
  def checkJust[M <: ModuleBase](activations: String): Unit = macro StaticPluginCheckerMacro.implDefault[M, NoModuleRequirements]
  def check[M <: ModuleBase, R <: ModuleRequirements](activations: String): Unit = macro StaticPluginCheckerMacro.implDefault[M, R]
  def checkWithConfig[M <: ModuleBase, R <: ModuleRequirements](activations: String, configFileRegex: String): Unit = macro StaticPluginCheckerMacro.implWithConfig[M, R]
  def checkWithPlugins[GcRoot <: ModuleBase, R <: ModuleRequirements](pluginPath: String, activations: String): Unit =
    macro StaticPluginCheckerMacro.implWithPlugin[GcRoot, R]
  def checkWithPluginsConfig[GcRoot <: ModuleBase, R <: ModuleRequirements](pluginPath: String, activations: String, configFileRegex: String): Unit =
    macro StaticPluginCheckerMacro.implWithPluginConfig[GcRoot, R]
}
