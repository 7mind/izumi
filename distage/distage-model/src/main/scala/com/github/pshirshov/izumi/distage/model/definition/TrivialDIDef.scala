package com.github.pshirshov.izumi.distage.model.definition

final case class TrivialDIDef(bindings: Set[Binding]) extends ContextDefinition {
  override def +(binding: Binding): ContextDefinition = TrivialDIDef(this.bindings + binding)
  override def ++(that: ContextDefinition): TrivialDIDef = TrivialDIDef(this.bindings ++ bindings)
}

object TrivialDIDef extends BindingDSL(Set.empty)
