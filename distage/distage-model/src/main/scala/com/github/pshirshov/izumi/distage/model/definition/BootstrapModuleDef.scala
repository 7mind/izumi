package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.definition.dsl.ModuleDefDSL

trait BootstrapModuleDef extends BootstrapModule with ModuleDefDSL

object BootstrapModuleDef {
  implicit val bootstrapModuleDefApi: ModuleMake[BootstrapModuleDef] = b =>
    new BootstrapModuleDef {
      override val bindings: Set[Binding] = b
    }
}
