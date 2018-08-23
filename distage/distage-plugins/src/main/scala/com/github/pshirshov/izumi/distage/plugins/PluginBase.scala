package com.github.pshirshov.izumi.distage.plugins

import com.github.pshirshov.izumi.distage.model.definition.{Binding, ModuleApi, ModuleBase}

trait PluginBase extends ModuleBase

object PluginBase {
  def empty: PluginBase = new PluginBase {
    override def bindings: Set[Binding] = Set.empty
  }

  def simple(bindings: Set[Binding]): PluginBase = {
    val b = bindings
    new PluginBase {
      override def bindings: Set[Binding] = b
    }
  }

  implicit object PluginBaseOps extends ModuleApi[PluginBase] {
    override def empty: PluginBase = PluginBase.empty

    override def simple(bindings: Set[Binding]): PluginBase = PluginBase.simple(bindings)
  }
}
