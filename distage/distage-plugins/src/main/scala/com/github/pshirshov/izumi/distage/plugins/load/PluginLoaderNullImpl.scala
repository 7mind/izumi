package com.github.pshirshov.izumi.distage.plugins.load

import com.github.pshirshov.izumi.distage.plugins.PluginBase

object PluginLoaderNullImpl extends PluginLoader {
  override def load(): Seq[PluginBase] = Seq.empty
}
