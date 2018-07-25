package com.github.pshirshov.izumi.distage.plugins.load

import com.github.pshirshov.izumi.distage.plugins.PluginBase

trait PluginLoader {
  def load(): Seq[PluginBase]
}
