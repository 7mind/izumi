package com.github.pshirshov.izumi.distage.model.definition

trait BootstrapContextModule extends BootstrapModule {
  override type Self <: BootstrapContextModule
}

object BootstrapContextModule {
  def empty: BootstrapContextModule = make(Set.empty)

  def make(bindings: Set[Binding]): BootstrapContextModule = {
    val b = bindings
    new BootstrapContextModule {
      override val bindings: Set[Binding] = b
    }
  }

  implicit val BootstrapBaseModuleApi: ModuleMake[BootstrapContextModule] = BootstrapContextModule.make
}
