package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

import scala.language.implicitConversions

trait AbstractModuleDef {
  protected type Impl <: AbstractModuleDef

  protected def make(bindings: Set[Binding]): Impl

  def bindings: Set[Binding]

  final def +(binding: Binding): Impl = {
    make(this.bindings + binding)
  }

  final def ++(that: AbstractModuleDef): Impl = {
    make(this.bindings ++ that.bindings)
  }

  final def overridenBy(that: AbstractModuleDef): Impl = {
    // we replace existing items in-place and appending new at the end
    val overrides: Map[DIKey, Binding] = that.bindings.map(b => b.key -> b).toMap
    val overriden: Set[Binding] = this.bindings.map(b => overrides.getOrElse(b.key, b))

    val index: Set[DIKey] = overriden.map(_.key)
    val appended: Set[Binding] = that.bindings.filterNot(b => index.contains(b.key))

    make(overriden ++ appended)
  }

  override def equals(obj: scala.Any): Boolean = obj match {
    case that: AbstractModuleDef => bindings == that.bindings
    case _ => false
  }

  override def hashCode(): Int = bindings.hashCode()
}

trait ModuleDef extends AbstractModuleDef {

  override type Impl = AbstractModuleDef

  protected def make(bindings: Set[Binding]): Impl = TrivialModuleDef(bindings)
}


trait PluginDef extends ModuleDef {

}

object AbstractModuleDef {
  implicit def bindingSetContextDefinition[T <: Binding](set: Set[T]): AbstractModuleDef =
    TrivialModuleDef(set.toSet)
}
