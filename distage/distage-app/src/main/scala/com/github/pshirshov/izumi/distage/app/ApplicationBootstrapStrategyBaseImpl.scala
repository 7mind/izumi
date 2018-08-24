package com.github.pshirshov.izumi.distage.app

import com.github.pshirshov.izumi.distage.model.definition.{BootstrapModule, ModuleBase}
import com.github.pshirshov.izumi.distage.plugins.load.{PluginLoader, PluginLoaderDefaultImpl, PluginLoaderNullImpl}
import com.github.pshirshov.izumi.distage.plugins.merge.{PluginMergeStrategy, SimplePluginMergeStrategy}
import com.github.pshirshov.izumi.distage.plugins.{LoadedPlugins, PluginBase}
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks

abstract class ApplicationBootstrapStrategyBaseImpl[CommandlineConfig]
(
  override val context: BootstrapContext[CommandlineConfig]
) extends ApplicationBootstrapStrategy[CommandlineConfig] {
  def mergeStrategy(bs: Seq[PluginBase], app: Seq[PluginBase]): PluginMergeStrategy[LoadedPlugins] = {
    Quirks.discard(bs, app)
    SimplePluginMergeStrategy
  }

  def bootstrapModules(bs: LoadedPlugins, app: LoadedPlugins): Seq[BootstrapModule] = {
    Quirks.discard(bs, app)
    Seq.empty
  }

  def appModules(bs: LoadedPlugins, app: LoadedPlugins): Seq[ModuleBase] = {
    Quirks.discard(bs, app)
    Seq.empty
  }

  def mkBootstrapLoader(): PluginLoader = PluginLoaderNullImpl

  def mkLoader(): PluginLoader = new PluginLoaderDefaultImpl(context.pluginConfig)
}
