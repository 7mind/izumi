package izumi.distage.model.definition

trait BootstrapModule extends ModuleBase

object BootstrapModule {
  def empty: BootstrapModule = make(Set.empty)

  def make(bindings: Set[Binding]): BootstrapModule = {
    val b = bindings
    new BootstrapModule {
      override val bindings: Set[Binding] = b
    }
  }

  implicit val BootstrapModuleApi: ModuleMake[BootstrapModule] = BootstrapModule.make
}
