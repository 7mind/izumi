package com.github.pshirshov.izumi.distage.plugins.merge

import com.github.pshirshov.izumi.distage.plugins.{MergedPlugins, PluginBase}

trait PluginMergeStrategy {
  def merge(defs: Seq[PluginBase]): MergedPlugins
}
