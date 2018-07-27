package com.github.pshirshov.izumi.distage.plugins

import com.github.pshirshov.izumi.distage.model.definition.ModuleBase

sealed trait LoadedPlugins {
  def definition: ModuleBase
}

object LoadedPlugins {

  final case class JustPlugins(definition: ModuleBase) extends LoadedPlugins

}
