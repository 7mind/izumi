package izumi.distage.roles.services

import distage.plugins.{PluginBase, PluginConfig}
import izumi.distage.plugins.load.PluginLoader
import izumi.distage.roles.BootstrapConfig
import izumi.distage.roles.services.PluginSource.AllLoadedPlugins

trait PluginSource {
  def load(): AllLoadedPlugins

  final def map(f: AllLoadedPlugins => AllLoadedPlugins): PluginSource = () => f(load())
}

object PluginSource {
  def apply(bootstrapConfig: BootstrapConfig): PluginSource = {
    PluginSource(bootstrapConfig.pluginConfig, bootstrapConfig.bootstrapPluginConfig)
  }
  def apply(pluginConfig: PluginConfig, bootstrapPluginConfig: Option[PluginConfig] = None): PluginSource = {
    new Impl(PluginLoader(pluginConfig), bootstrapPluginConfig.fold(PluginLoader.empty)(PluginLoader(_)))
  }
  def apply(pluginLoader: PluginLoader, bootstrapPluginLoader: PluginLoader): PluginSource = {
    new Impl(pluginLoader, bootstrapPluginLoader)
  }
  def apply(pluginLoader: PluginLoader): PluginSource = {
    new Impl(pluginLoader, PluginLoader.empty)
  }
  def apply(allLoadedPlugins: AllLoadedPlugins): PluginSource = {
    () =>
      allLoadedPlugins
  }
  def apply(plugins: Seq[PluginBase], bootstrapPlugins: Seq[PluginBase]): PluginSource = {
    PluginSource(AllLoadedPlugins(plugins, bootstrapPlugins))
  }
  def apply(plugins: Seq[PluginBase]): PluginSource = {
    PluginSource(plugins, Seq.empty)
  }

  final class Impl(pluginLoader: PluginLoader, bootstrapPluginLoader: PluginLoader) extends PluginSource {
    def load(): AllLoadedPlugins = AllLoadedPlugins(bootstrapPluginLoader.load(), pluginLoader.load())
  }

  final case class AllLoadedPlugins(
    bootstrap: Seq[PluginBase],
    app: Seq[PluginBase],
  )
}
