package com.github.pshirshov.izumi.distage.plugins

import com.github.pshirshov.izumi.distage.model.definition.{Module, ModuleDef}

trait PluginDef extends PluginBase with ModuleDef {
  type Self <: PluginBase with Module
}
