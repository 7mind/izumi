package com.github.pshirshov.izumi.distage.plugins.merge

import com.github.pshirshov.izumi.distage.plugins.{LoadedPlugins, PluginBase}


trait PluginMergeStrategy[T <: LoadedPlugins] {
  def merge[D <: PluginBase](defs: Seq[D]): T
}
