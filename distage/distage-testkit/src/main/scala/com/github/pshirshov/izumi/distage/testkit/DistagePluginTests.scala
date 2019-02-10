package com.github.pshirshov.izumi.distage.testkit

import com.github.pshirshov.izumi.distage.model.definition.{BindingTag, ModuleBase}
import com.github.pshirshov.izumi.distage.plugins.PluginBase
import com.github.pshirshov.izumi.distage.plugins.load.PluginLoaderDefaultImpl
import com.github.pshirshov.izumi.distage.plugins.load.PluginLoaderDefaultImpl.PluginConfig
import com.github.pshirshov.izumi.distage.plugins.merge.ConfigurablePluginMergeStrategy.PluginMergeConfig
import com.github.pshirshov.izumi.distage.plugins.merge.{ConfigurablePluginMergeStrategy, PluginMergeStrategy}

trait DistagePluginTests extends DistageTests {
  protected lazy val loadedPlugins: Seq[PluginBase] = {
    new PluginLoaderDefaultImpl(
      PluginConfig(debug = false, pluginPackages, Seq.empty)
    ).load()
  }

  protected def mergeStrategy: PluginMergeStrategy =
    new ConfigurablePluginMergeStrategy(PluginMergeConfig(
      disabledTags
    , Set.empty
    , Set.empty
    , Map.empty
    ))

  protected def makeBindings: ModuleBase = {
    val primaryModule = mergeStrategy.merge(loadedPlugins).definition
    primaryModule
  }

  protected def pluginPackages: Seq[String] = {
    Seq(this.getClass.getPackage.getName)
  }

  protected def disabledTags: BindingTag.Expressions.Expr = BindingTag.Expressions.False
}
