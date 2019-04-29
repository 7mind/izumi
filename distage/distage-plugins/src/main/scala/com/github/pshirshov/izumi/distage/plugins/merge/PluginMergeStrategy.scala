package com.github.pshirshov.izumi.distage.plugins.merge

import com.github.pshirshov.izumi.distage.model.definition.ModuleBase
import com.github.pshirshov.izumi.distage.plugins.PluginBase

trait PluginMergeStrategy {
  def merge(defs: Seq[PluginBase]): ModuleBase
}
