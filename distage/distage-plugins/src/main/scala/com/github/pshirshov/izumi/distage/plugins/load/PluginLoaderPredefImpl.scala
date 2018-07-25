package com.github.pshirshov.izumi.distage.plugins.load

import com.github.pshirshov.izumi.distage.plugins.PluginBase

class PluginLoaderPredefImpl(plugins: Seq[PluginBase]) extends PluginLoader {
  override def load(): Seq[PluginBase] = plugins
}
