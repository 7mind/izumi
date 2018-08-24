package com.github.pshirshov.izumi.distage.app

import com.github.pshirshov.izumi.distage.model.definition.{BootstrapModule, ModuleBase}
import com.github.pshirshov.izumi.distage.plugins.load.PluginLoader
import com.github.pshirshov.izumi.distage.plugins.merge.PluginMergeStrategy
import com.github.pshirshov.izumi.distage.plugins.{LoadedPlugins, PluginBase}
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter

trait ApplicationBootstrapStrategy[CommandlineConfig] {

  type Context = BootstrapContext[CommandlineConfig]

  def context: Context

  def mergeStrategy(bs: Seq[PluginBase], app: Seq[PluginBase]): PluginMergeStrategy[LoadedPlugins]

  def router(): LogRouter

  def bootstrapModules(bs: LoadedPlugins, app: LoadedPlugins): Seq[BootstrapModule]

  def appModules(bs: LoadedPlugins, app: LoadedPlugins): Seq[ModuleBase]

  def mkBootstrapLoader(): PluginLoader

  def mkLoader(): PluginLoader
}
