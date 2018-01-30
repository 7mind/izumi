package com.github.pshirshov.izumi.distage.model.definition

case class TrivialDIDef(bindings: Seq[Binding]) extends ContextDefinition

object TrivialDIDef extends BindingDSL {
  override final val bindings: Seq[Binding] = Seq()
}
