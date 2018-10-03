package com.github.pshirshov.izumi.distage.testkit

import com.github.pshirshov.izumi.distage.model.definition.ModuleBase
import com.github.pshirshov.izumi.distage.plugins.PluginBase
import com.github.pshirshov.izumi.distage.plugins.load.PluginLoaderDefaultImpl
import com.github.pshirshov.izumi.distage.plugins.load.PluginLoaderDefaultImpl.PluginConfig
import com.github.pshirshov.izumi.distage.plugins.merge.ConfigurablePluginMergeStrategy
import com.github.pshirshov.izumi.distage.plugins.merge.ConfigurablePluginMergeStrategy.PluginMergeConfig
import com.github.pshirshov.izumi.fundamentals.tags.TagExpr

trait DistagePluginTests extends DistageTests {
  lazy val loadedPlugins: Seq[PluginBase] = {
    new PluginLoaderDefaultImpl(
      PluginConfig(debug = false, pluginPackages, Seq.empty)
    ).load()
  }

  protected def makeBindings: ModuleBase = {
    val mergeStrategy = new ConfigurablePluginMergeStrategy(PluginMergeConfig(
      disabledTags
      , Set.empty
      , Set.empty
      , Map.empty
    ))

    val primaryModule = mergeStrategy.merge(loadedPlugins).definition
    primaryModule
  }

  protected def pluginPackages: Seq[String] = {
    Seq(this.getClass.getPackage.getName)
  }

  protected def disabledTags: TagExpr.Strings.Expr = TagExpr.Strings.False
}
