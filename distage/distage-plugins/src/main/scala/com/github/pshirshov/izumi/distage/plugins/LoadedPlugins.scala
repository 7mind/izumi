package com.github.pshirshov.izumi.distage.plugins

import com.github.pshirshov.izumi.distage.model.definition.ModuleDef

trait LoadedPlugins {
  def definition: ModuleDef
}

final case class JustLoadedPlugins(definition: ModuleDef) extends LoadedPlugins
