package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

import scala.language.implicitConversions

trait ContextDefinition {

  def bindings: Set[Binding]

  override def equals(obj: scala.Any): Boolean = obj match {
    case that: ContextDefinition => bindings == that.bindings
    case _ => false
  }

  def +(binding: Binding): ContextDefinition

  def ++(that: ContextDefinition): ContextDefinition

  def overridenBy(that: ContextDefinition): ContextDefinition = {
    // we replace existing items in-place and appending new at the end
    val overrides: Map[DIKey, Binding] = that.bindings.map(b => b.target -> b).toMap
    val overriden: Set[Binding] = this.bindings.map(b => overrides.getOrElse(b.target, b))

    val index = overriden.map(_.target)
    val appended: Set[Binding] = that.bindings.filterNot(b => index.contains(b.target))

    TrivialDIDef(overriden ++ appended)
  }

}

object ContextDefinition {
  implicit def bindingSetContextDefinition[T <: Binding](set: Set[T]): ContextDefinition =
    TrivialDIDef(set.toSet)
}
