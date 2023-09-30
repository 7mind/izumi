package izumi.distage.model.definition

trait BootstrapContextModule extends BootstrapModule

object BootstrapContextModule {
  def empty: BootstrapContextModule = make(Set.empty)

  def make(bindings: Set[Binding]): BootstrapContextModule = {
    val b = bindings
    new BootstrapContextModule {
      override val bindings: Set[Binding] = b
    }
  }

  def from(module: ModuleBase): BootstrapContextModule = make(module.bindings)

  implicit val BootstrapBaseModuleApi: ModuleMake[BootstrapContextModule] = BootstrapContextModule.make
}
