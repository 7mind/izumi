package com.github.pshirshov.izumi.distage.plugins.merge

import com.github.pshirshov.izumi.distage.plugins.{MergedPlugins, PluginBase}

object SimplePluginMergeStrategy extends PluginMergeStrategy {

  override def mergeBootstrap(defs: Seq[PluginBase]): MergedPlugins = merge(defs)

  override def merge(defs: Seq[PluginBase]): MergedPlugins = {
    val merged = defs.merge
    MergedPlugins(merged)
  }

}
