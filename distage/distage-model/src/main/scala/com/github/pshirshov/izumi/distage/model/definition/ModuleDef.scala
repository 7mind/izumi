package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

import scala.language.implicitConversions

trait ModuleDef {
  protected type Impl = ModuleDef

  protected def make(bindings: Set[Binding]): Impl = TrivialModuleDef(bindings)

  def bindings: Set[Binding]

  final def +(binding: Binding): Impl = {
    make(this.bindings + binding)
  }

  final def ++(that: ModuleDef): Impl = {
    make(this.bindings ++ that.bindings)
  }

  final def overridenBy(that: ModuleDef): Impl = {
    // we replace existing items in-place and appending new at the end
    val overrides: Map[DIKey, Binding] = that.bindings.map(b => b.target -> b).toMap
    val overriden: Set[Binding] = this.bindings.map(b => overrides.getOrElse(b.target, b))

    val index = overriden.map(_.target)
    val appended: Set[Binding] = that.bindings.filterNot(b => index.contains(b.target))

    make(overriden ++ appended)
  }

  override def equals(obj: scala.Any): Boolean = obj match {
    case that: ModuleDef => bindings == that.bindings
    case _ => false
  }

  override def hashCode(): Int = bindings.hashCode()
}

object ModuleDef {
  implicit def bindingSetContextDefinition[T <: Binding](set: Set[T]): ModuleDef =
    TrivialModuleDef(set.toSet)
}
