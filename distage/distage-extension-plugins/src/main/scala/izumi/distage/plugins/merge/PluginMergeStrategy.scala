package izumi.distage.plugins.merge

import izumi.distage.model.definition.ModuleBase

trait PluginMergeStrategy {
  def merge(defs: Seq[ModuleBase]): ModuleBase
}
