package com.github.pshirshov.izumi.distage.plugins

import com.github.pshirshov.izumi.distage.model.definition.{Binding, ModuleDef, ModuleMake}

trait PluginDef extends PluginBase with ModuleDef

object PluginDef {
  implicit val pluginBaseModuleApi: ModuleMake[PluginDef] = b =>
    new PluginDef {
      override val bindings: Set[Binding] = b
    }
}
