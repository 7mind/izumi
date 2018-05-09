package com.github.pshirshov.izumi.distage.model.definition

final case class SimpleModuleDef(bindings: Set[Binding] = Set.empty[Binding]) extends ModuleBase

object SimpleModuleDef {
  def empty: SimpleModuleDef = SimpleModuleDef(Set.empty[Binding])
}
