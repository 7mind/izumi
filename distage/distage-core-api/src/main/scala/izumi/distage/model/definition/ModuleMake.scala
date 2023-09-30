package izumi.distage.model.definition

import scala.annotation.unchecked.uncheckedVariance

// FIXME: the variance on T is a workaround a Scala 3 bug https://github.com/lampepfl/dotty/issues/16431
trait ModuleMake[+T <: ModuleBase] extends ModuleMake.Aux[T @uncheckedVariance, T]

object ModuleMake {
  def apply[T <: ModuleBase: ModuleMake]: ModuleMake[T] = implicitly

  sealed trait Aux[-S, +T <: ModuleBase] {
    def make(bindings: Set[Binding]): T
    final def from(moduleBase: ModuleBase): T = make(moduleBase.bindings)

    final def empty: T = make(Set.empty)
  }
}
