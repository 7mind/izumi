package izumi.distage.model.definition

trait Module extends ModuleBase

object Module {
  def empty: Module = make(Set.empty)

  def make(bindings: Set[Binding]): Module = {
    val b = bindings
    new Module {
      override val bindings: Set[Binding] = b
    }
  }

  def from(module: ModuleBase): Module = make(module.bindings)

  implicit val moduleApi: ModuleMake[Module] = Module.make
}
