package izumi.distage.plugins

import izumi.distage.model.definition.{Binding, BootstrapModule, ModuleBase, ModuleMake}

trait BootstrapPlugin extends PluginBase with BootstrapModule

object BootstrapPlugin {
  def empty: BootstrapPlugin = make(Set.empty)

  def make(bindings: Set[Binding]): BootstrapPlugin = {
    val b = bindings
    new BootstrapPlugin {
      override val bindings: Set[Binding] = b
    }
  }

  def from(module: ModuleBase): BootstrapPlugin = make(module.bindings)

  implicit val bootstrapPluginModuleApi: ModuleMake[BootstrapPlugin] = BootstrapPlugin.make
}
