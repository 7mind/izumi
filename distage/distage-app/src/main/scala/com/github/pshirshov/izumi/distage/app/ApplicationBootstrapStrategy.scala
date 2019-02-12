package com.github.pshirshov.izumi.distage.app

import com.github.pshirshov.izumi.distage.model.definition.{BootstrapModule, Module}
import com.github.pshirshov.izumi.distage.plugins.load.PluginLoader
import com.github.pshirshov.izumi.distage.plugins.merge.PluginMergeStrategy
import com.github.pshirshov.izumi.distage.plugins.{LoadedPlugins, PluginBase}
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter
import distage.DIKey

trait ApplicationBootstrapStrategy {
  def context: BootstrapConfig

  def router(): LogRouter

  def mergeStrategy(bs: Seq[PluginBase], app: Seq[PluginBase]): PluginMergeStrategy

  def bootstrapModules(bs: LoadedPlugins, app: LoadedPlugins): Seq[BootstrapModule]

  def appModules(bs: LoadedPlugins, app: LoadedPlugins): Seq[Module]

  def gcRoots(bs: LoadedPlugins, app: LoadedPlugins): Set[DIKey]

  def mkBootstrapLoader(): PluginLoader = {
    context.bootstrapPluginConfig.fold(PluginLoader.empty)(PluginLoader(_))
  }

  def mkLoader(): PluginLoader = {
    PluginLoader(context.pluginConfig)
  }
}
