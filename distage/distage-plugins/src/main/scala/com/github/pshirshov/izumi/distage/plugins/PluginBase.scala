package com.github.pshirshov.izumi.distage.plugins

import com.github.pshirshov.izumi.distage.model.definition.{Binding, Module, ModuleMake}

trait PluginBase extends Module {
  override type Self <: PluginBase
}

object PluginBase {
  def empty: PluginBase = make(Set.empty)

  def make(bindings: Set[Binding]): PluginBase = {
    val b = bindings
    new PluginBase {
      override val bindings: Set[Binding] = b
    }
  }

  implicit val pluginBaseModuleApi: ModuleMake[PluginBase] = PluginBase.make
}
