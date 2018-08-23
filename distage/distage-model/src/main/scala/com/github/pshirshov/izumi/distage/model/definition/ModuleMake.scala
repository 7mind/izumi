package com.github.pshirshov.izumi.distage.model.definition

trait ModuleMake[T <: ModuleBase] {
  def make(bindings: Set[Binding]): T

  final def empty: T = make(Set.empty)
}

object ModuleMake {
  def apply[T <: ModuleBase: ModuleMake]: ModuleMake[T] = implicitly
}
