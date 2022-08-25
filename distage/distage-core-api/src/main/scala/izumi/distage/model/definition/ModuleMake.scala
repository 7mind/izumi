package izumi.distage.model.definition

import scala.annotation.unchecked.uncheckedVariance

trait ModuleMake[+T <: ModuleBase] extends ModuleMake.Aux[T @uncheckedVariance, T]

object ModuleMake {
  def apply[T <: ModuleBase: ModuleMake]: ModuleMake[T] = implicitly

  sealed trait Aux[-S, +T <: ModuleBase] {
    def make(bindings: Set[Binding]): T
    final def empty: T = make(Set.empty)
  }
}
