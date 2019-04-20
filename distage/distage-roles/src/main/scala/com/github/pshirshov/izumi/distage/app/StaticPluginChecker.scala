package com.github.pshirshov.izumi.distage.app

import com.github.pshirshov.izumi.distage.app.plugin.macros.StaticPluginCheckerMacro
import distage.plugins.PluginBase

import scala.language.experimental.macros

object StaticPluginChecker {

  def check[GcRoot <: PluginBase, R <: ModuleRequirements](disableTags: String): Unit = macro StaticPluginCheckerMacro.implDefault[GcRoot, R]

  def checkWithConfig[GcRoot <: PluginBase, R <: ModuleRequirements](disableTags: String, configFileRegex: String): Unit = macro StaticPluginCheckerMacro.implWithConfig[GcRoot, R]

  def checkWithPlugins[GcRoot <: PluginBase, R <: ModuleRequirements](pluginPath: String, disableTags: String): Unit = macro StaticPluginCheckerMacro.implWithPlugin[GcRoot, R]

  def checkWithPluginsConfig[GcRoot <: PluginBase, R <: ModuleRequirements](pluginPath: String, disableTags: String, configFileRegex: String): Unit = macro StaticPluginCheckerMacro.implWithPluginConfig[GcRoot, R]

}
