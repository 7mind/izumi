package izumi.distage.plugins

import izumi.distage.model.definition.{Binding, ModuleBase, ModuleMake}

trait PluginBase extends ModuleBase

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
