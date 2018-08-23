package com.github.pshirshov.izumi.distage.model.definition

trait BootstrapModuleDef extends DSLModuleDef with BootstrapModule {

}

object BootstrapModuleDef {
  def empty: BootstrapModuleDef = new BootstrapModuleDef {
    override def bindings: Set[Binding] = Set.empty
  }

  def simple(bindings: Set[Binding]): BootstrapModuleDef = {
    val b = bindings
    new BootstrapModuleDef {
      override def bindings: Set[Binding] = b
    }
  }

  implicit object BootstrapModuleDefOps extends ModuleApi[BootstrapModuleDef] {
    override def empty: BootstrapModuleDef = BootstrapModuleDef.empty

    override def simple(bindings: Set[Binding]): BootstrapModuleDef = BootstrapModuleDef.simple(bindings)
  }
}
