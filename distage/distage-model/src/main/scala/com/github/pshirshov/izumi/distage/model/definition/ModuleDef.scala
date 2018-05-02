package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

import scala.language.implicitConversions

trait ModuleDef {
  def bindings: Set[Binding]

  override def equals(obj: scala.Any): Boolean = obj match {
    case that: ModuleDef => bindings == that.bindings
    case _ => false
  }

  override def hashCode(): Int = bindings.hashCode()
}

trait PluginDef extends ModuleDef

case class TrivialModuleDef(bindings: Set[Binding]) extends ModuleDef

object TrivialModuleDef extends TrivialModuleDef(Set.empty)

object ModuleDef {
  implicit final class ModuleDefCombine(private val moduleDef: ModuleDef) {
    def ++(binding: Binding): ModuleDef = {
      TrivialModuleDef(moduleDef.bindings + binding)
    }

    def ++(that: ModuleDef): ModuleDef = {
      TrivialModuleDef(moduleDef.bindings ++ that.bindings)
    }

    def overridenBy(that: ModuleDef): ModuleDef = {
      // we replace existing items in-place and appending new at the end
      val overrides: Map[DIKey, Binding] = that.bindings.map(b => b.key -> b).toMap
      val overriden: Set[Binding] = moduleDef.bindings.map(b => overrides.getOrElse(b.key, b))

      val index: Set[DIKey] = overriden.map(_.key)
      val appended: Set[Binding] = that.bindings.filterNot(b => index.contains(b.key))

      TrivialModuleDef(overriden ++ appended)
    }
  }

  // FIXME: remove BindingDSL
  implicit def moduleDefToBindingDSL(mod: ModuleDef): BindingDSL =
    new BindingDSL(mod.bindings)

}
