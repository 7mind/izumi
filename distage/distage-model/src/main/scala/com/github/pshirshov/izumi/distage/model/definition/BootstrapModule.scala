package com.github.pshirshov.izumi.distage.model.definition

trait BootstrapModule extends ModuleBase {

}

object BootstrapModule {
  def empty: BootstrapModule = new BootstrapModule {
    override def bindings: Set[Binding] = Set.empty
  }

  def simple(bindings: Set[Binding]): BootstrapModule = {
    val b = bindings
    new BootstrapModule {
      override def bindings: Set[Binding] = b
    }
  }

  implicit object BootstrapModuleOps extends ModuleApi[BootstrapModule] {
    override def empty: BootstrapModule = BootstrapModule.empty

    override def simple(bindings: Set[Binding]): BootstrapModule = BootstrapModule.simple(bindings)
  }
}
