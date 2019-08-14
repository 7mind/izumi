package izumi.distage.plugins

import izumi.distage.model.definition.{BootstrapModule, BootstrapModuleDef}

trait BootstrapPluginDef extends PluginBase with BootstrapModuleDef {
  type Self <: PluginBase with BootstrapModule
}
