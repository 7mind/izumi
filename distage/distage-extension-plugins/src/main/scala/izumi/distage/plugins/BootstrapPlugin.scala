package izumi.distage.plugins

import izumi.distage.model.definition.{Binding, BootstrapModule, ModuleMake}

trait BootstrapPlugin extends PluginBase with BootstrapModule

object BootstrapPlugin {
  def empty: BootstrapPlugin = make(Set.empty)

  def make(bindings: Set[Binding]): BootstrapPlugin = {
    val b = bindings
    new BootstrapPlugin {
      override val bindings: Set[Binding] = b
    }
  }

  implicit val bootstrapPluginModuleApi: ModuleMake[BootstrapPlugin] = BootstrapPlugin.make
}
