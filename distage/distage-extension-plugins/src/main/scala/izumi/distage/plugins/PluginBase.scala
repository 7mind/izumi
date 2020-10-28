package izumi.distage.plugins

import izumi.distage.model.definition.{Binding, ModuleBase, ModuleMake}

/**
  * Non-abstract class or object inheritors of [[PluginBase]] will be found on the classpath by `distage-extension-plugins`
  * scanning machinery. ([[izumi.distage.plugins.load.PluginLoader]])
  *
  * @see [[https://izumi.7mind.io/distage/distage-framework#plugins Plugins]]
  * @see [[izumi.distage.plugins.PluginDef]]
  */
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
