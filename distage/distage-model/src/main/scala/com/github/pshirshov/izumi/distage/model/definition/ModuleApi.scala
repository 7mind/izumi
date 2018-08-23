package com.github.pshirshov.izumi.distage.model.definition

trait ModuleApi[T <: ModuleBase] {
  def empty: T

  def simple(bindings: Set[Binding]): T
}
