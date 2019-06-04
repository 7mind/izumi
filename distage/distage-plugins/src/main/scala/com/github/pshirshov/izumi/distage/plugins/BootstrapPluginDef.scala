package com.github.pshirshov.izumi.distage.plugins

import com.github.pshirshov.izumi.distage.model.definition.{BootstrapModule, BootstrapModuleDef}

trait BootstrapPluginDef extends PluginBase with BootstrapModuleDef {
  type Self <: PluginBase with BootstrapModule
}
