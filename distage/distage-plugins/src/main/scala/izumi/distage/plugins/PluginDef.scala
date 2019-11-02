package izumi.distage.plugins

import izumi.distage.model.definition.{Module, ModuleDef}

trait PluginDef extends PluginBase with ModuleDef {
  type Self <: PluginBase with Module
}
