package com.github.pshirshov.izumi.distage.app

import com.github.pshirshov.izumi.distage.model.definition.{BootstrapModule, Module}
import com.github.pshirshov.izumi.distage.plugins.load.PluginLoader
import com.github.pshirshov.izumi.distage.plugins.merge.PluginMergeStrategy
import com.github.pshirshov.izumi.distage.plugins.{MergedPlugins, PluginBase}
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter
import distage.DIKey

@deprecated("Migrate to new role infra", "2019-04-19")
trait ApplicationBootstrapStrategy {
  def context: BootstrapConfig

  def router(): LogRouter

  def mergeStrategy(bs: Seq[PluginBase], app: Seq[PluginBase]): PluginMergeStrategy

  def bootstrapModules(bs: MergedPlugins, app: MergedPlugins): Seq[BootstrapModule]

  def appModules(bs: MergedPlugins, app: MergedPlugins): Seq[Module]

  def gcRoots(bs: MergedPlugins, app: MergedPlugins): Set[DIKey]

  def mkBootstrapLoader(): PluginLoader = {
    context.bootstrapPluginConfig.fold(PluginLoader.empty)(PluginLoader(_))
  }

  def mkLoader(): PluginLoader = {
    PluginLoader(context.pluginConfig)
  }
}
