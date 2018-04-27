package com.github.pshirshov.izumi.distage.model.definition

trait ContextDefinition {
  def bindings: Seq[Binding]

  def overridenBy(another: ContextDefinition): ContextDefinition = {
    // we replace existing items in-place and appending new at the end
    val overrides = another.bindings.map(b =>  b.target -> b).toMap
    val overriden = bindings.map(b => overrides.getOrElse(b.target, b))

    val index = overriden.map(_.target).toSet
    val appended = another.bindings.filterNot(b => index.contains(b.target))

    TrivialDIDef(overriden ++ appended)
  }
}
