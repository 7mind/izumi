package com.github.pshirshov.izumi.distage.plugins.merge

import com.github.pshirshov.izumi.distage.plugins.{LoadedPlugins, PluginBase}

object SimplePluginMergeStrategy extends PluginMergeStrategy {

  override def merge(defs: Seq[PluginBase]): LoadedPlugins = {
    val merged = defs.merge
    LoadedPlugins(merged)
  }

}
