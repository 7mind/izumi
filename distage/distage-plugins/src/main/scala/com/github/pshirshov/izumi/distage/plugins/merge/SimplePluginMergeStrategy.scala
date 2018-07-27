package com.github.pshirshov.izumi.distage.plugins.merge

import com.github.pshirshov.izumi.distage.plugins.LoadedPlugins.JustPlugins
import com.github.pshirshov.izumi.distage.plugins.{LoadedPlugins, PluginBase}

object SimplePluginMergeStrategy extends PluginMergeStrategy[LoadedPlugins] {
  override def merge[D <: PluginBase](defs: Seq[D]): LoadedPlugins = {
    val merged = defs.merge
    JustPlugins(merged)
  }
}
