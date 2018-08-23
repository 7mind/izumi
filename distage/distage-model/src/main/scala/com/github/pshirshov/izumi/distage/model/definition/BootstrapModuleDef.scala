package com.github.pshirshov.izumi.distage.model.definition

trait BootstrapModuleDef extends BootstrapModule with ModuleDefDSL

object BootstrapModuleDef {
  implicit val bootstrapModuleDefApi: ModuleMake[BootstrapModuleDef] = b =>
    new BootstrapModuleDef {
      override val bindings: Set[Binding] = b
    }
}
