package izumi.distage.model.definition

trait Module extends ModuleBase {
  override type Self <: Module
}

object Module {
  def empty: Module = make(Set.empty)

  def make(bindings: Set[Binding]): Module = {
    val b = bindings
    new Module {
      override val bindings: Set[Binding] = b
    }
  }

  implicit val moduleApi: ModuleMake[Module] = Module.make
}
