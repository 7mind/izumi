package izumi.distage.plugins.merge

import izumi.distage.model.definition.ModuleBase
import izumi.distage.plugins.PluginBase

trait PluginMergeStrategy {
  def merge(defs: Seq[PluginBase]): ModuleBase
}
