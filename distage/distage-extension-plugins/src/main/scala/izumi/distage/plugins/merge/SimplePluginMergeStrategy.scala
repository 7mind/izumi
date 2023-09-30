package izumi.distage.plugins.merge

import izumi.distage.model.definition.ModuleBase

object SimplePluginMergeStrategy extends PluginMergeStrategy {
  override def merge(defs: Seq[ModuleBase]): ModuleBase = defs.merge
}
