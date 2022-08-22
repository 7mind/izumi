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

  implicit val BootstrapBaseModuleApi: ModuleMake[BootstrapContextModule] = new ModuleMake[BootstrapContextModule] {
    override def make(bindings: Set[Binding]): BootstrapContextModule = BootstrapContextModule.make(bindings)
  }
}
