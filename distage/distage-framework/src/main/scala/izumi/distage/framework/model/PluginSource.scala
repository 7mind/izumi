package izumi.distage.framework.model

import distage.plugins.{PluginBase, PluginConfig}
import izumi.distage.plugins.load.PluginLoader

trait PluginSource {
  def load(): AllLoadedPlugins
  def bootstrapConfig: Option[BootstrapConfig] = None

  final def map(f: AllLoadedPlugins => AllLoadedPlugins): PluginSource = () => f(load())
}

object PluginSource {
  def apply(bootstrapConfig: BootstrapConfig): PluginSource = {
    new Impl(PluginLoader(bootstrapConfig.pluginConfig), bootstrapConfig.bootstrapPluginConfig.fold(PluginLoader.empty)(PluginLoader(_)), Some(bootstrapConfig))
  }
  def apply(pluginConfig: PluginConfig, bootstrapPluginConfig: Option[PluginConfig] = None): PluginSource = {
    new Impl(PluginLoader(pluginConfig), bootstrapPluginConfig.fold(PluginLoader.empty)(PluginLoader(_)), None)
  }
  def apply(pluginLoader: PluginLoader, bootstrapPluginLoader: PluginLoader): PluginSource = {
    new Impl(pluginLoader, bootstrapPluginLoader, None)
  }
  def apply(pluginLoader: PluginLoader): PluginSource = {
    new Impl(pluginLoader, PluginLoader.empty, None)
  }
  def apply(allLoadedPlugins: AllLoadedPlugins): PluginSource = {
    () => allLoadedPlugins
  }
  def apply(plugins: Seq[PluginBase], bootstrapPlugins: Seq[PluginBase]): PluginSource = {
    PluginSource(AllLoadedPlugins(plugins, bootstrapPlugins))
  }
  def apply(plugins: Seq[PluginBase]): PluginSource = {
    PluginSource(plugins, Seq.empty)
  }

  def empty: PluginSource = PluginSource(AllLoadedPlugins(Nil, Nil))

  final class Impl(pluginLoader: PluginLoader, bootstrapPluginLoader: PluginLoader, override val bootstrapConfig: Option[BootstrapConfig]) extends PluginSource {
    def load(): AllLoadedPlugins = {
      val bsPlugins = bootstrapPluginLoader.load()
      val appPlugins = pluginLoader.load()
      AllLoadedPlugins(bsPlugins, appPlugins)
    }
  }
}
