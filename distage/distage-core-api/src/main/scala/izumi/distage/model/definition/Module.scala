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

  implicit val moduleApi: ModuleMake[Module] = new ModuleMake[Module] {
    override def make(bindings: Set[Binding]): Module = Module.make(bindings)
  }
}
