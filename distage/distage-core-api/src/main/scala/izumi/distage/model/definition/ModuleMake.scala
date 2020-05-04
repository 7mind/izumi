package izumi.distage.model.definition

trait ModuleMake[T <: ModuleBase] extends ModuleMake.Aux[T, T]

object ModuleMake {
  def apply[T <: ModuleBase: ModuleMake]: ModuleMake[T] = implicitly

  sealed trait Aux[-S, +T <: ModuleBase] {
    def make(bindings: Set[Binding]): T

    final def empty: T = make(Set.empty)

    type DivergenceRedirect
  }
}
