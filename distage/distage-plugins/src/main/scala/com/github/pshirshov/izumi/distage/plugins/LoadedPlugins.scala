package com.github.pshirshov.izumi.distage.plugins

import com.github.pshirshov.izumi.distage.model.definition.ModuleBase

trait LoadedPlugins {
  def definition: ModuleBase
}

final case class JustLoadedPlugins(definition: ModuleBase) extends LoadedPlugins
