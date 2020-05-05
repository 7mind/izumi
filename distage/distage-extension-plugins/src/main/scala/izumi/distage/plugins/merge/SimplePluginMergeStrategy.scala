package izumi.distage.plugins.merge

import izumi.distage.model.definition.ModuleBase
import izumi.distage.plugins.PluginBase

object SimplePluginMergeStrategy extends PluginMergeStrategy {
  override def merge(defs: Seq[PluginBase]): ModuleBase = defs.merge
}
