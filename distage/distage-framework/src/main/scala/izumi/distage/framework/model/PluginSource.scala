package izumi.distage.framework.model

import izumi.distage.model.definition.{BootstrapModule, Module}
import izumi.distage.plugins.load.PluginLoader
import izumi.distage.plugins.{PluginBase, PluginConfig}

sealed trait PluginSource {
  def load(): AllLoadedPlugins

  final def ++(that: PluginSource): PluginSource = PluginSource.Join(this, that)
  final def map(f: AllLoadedPlugins => AllLoadedPlugins): PluginSource = PluginSource.Map(this, f)

  final def ++(plugins: AllLoadedPlugins): PluginSource = map(_ ++ plugins)
  final def ++(appModule: Module): PluginSource = map(_ ++ appModule)
  final def ++(bootstrapModule: BootstrapModule): PluginSource = map(_ ++ bootstrapModule)

  final def overridenBy(plugins: AllLoadedPlugins): PluginSource = map(_ overridenBy plugins)
  final def overridenBy(appModule: Module): PluginSource = map(_ overridenBy appModule)
  final def overridenBy(bootstrapModule: BootstrapModule): PluginSource = map(_ overridenBy bootstrapModule)
}

object PluginSource {
  /** Load plugins from the specified packages */
  def apply(pluginConfig: PluginConfig): PluginSource = Load(pluginConfig, PluginConfig.empty)
  def apply(pluginConfig: PluginConfig, bootstrapPluginConfig: PluginConfig): PluginSource = Load(pluginConfig, bootstrapPluginConfig)

  /** Load plugins using the specified loaders */
  def apply(pluginLoader: PluginLoader): PluginSource = CustomLoad(pluginLoader, PluginLoader.empty)
  def apply(pluginLoader: PluginLoader, bootstrapPluginLoader: PluginLoader): PluginSource = CustomLoad(pluginLoader, bootstrapPluginLoader)

  /** Directly use the specified plugins */
  def apply(plugins: Seq[PluginBase]): PluginSource = Const(AllLoadedPlugins(plugins))
  def apply(plugins: Seq[PluginBase], bootstrapPlugins: Seq[PluginBase]): PluginSource = Const(AllLoadedPlugins(plugins, bootstrapPlugins))
  def apply(allLoadedPlugins: AllLoadedPlugins): PluginSource = Const(allLoadedPlugins)

  /** Empty */
  lazy val empty: PluginSource = Const(AllLoadedPlugins.empty)

  implicit final class PluginSourceMerge(private val pluginSources: Iterable[PluginSource]) extends AnyVal {
    def merge: PluginSource = pluginSources.foldLeft(PluginSource.empty)(_ ++ _)
  }

  final case class Join(a: PluginSource, b: PluginSource) extends PluginSource {
    override def load(): AllLoadedPlugins = a.load() ++ b.load()
  }
  final case class Map(a: PluginSource, f: AllLoadedPlugins => AllLoadedPlugins) extends PluginSource {
    override def load(): AllLoadedPlugins = f(a.load())
  }
  final case class Load(pluginConfig: PluginConfig, bootstrapPluginConfig: PluginConfig) extends PluginSource {
    override def load(): AllLoadedPlugins = {
      val appPlugins = PluginLoader(pluginConfig).load()
      val bsPlugins = PluginLoader(bootstrapPluginConfig).load()
      AllLoadedPlugins(appPlugins, bsPlugins)
    }
  }

  trait Custom extends PluginSource
  final case class Const(plugins: AllLoadedPlugins) extends PluginSource.Custom {
    override def load(): AllLoadedPlugins = plugins
  }
  final case class CustomLoad(pluginLoader: PluginLoader, bootstrapPluginLoader: PluginLoader) extends PluginSource {
    override def load(): AllLoadedPlugins = AllLoadedPlugins(app = pluginLoader.load(), bootstrap = bootstrapPluginLoader.load())
  }

}
