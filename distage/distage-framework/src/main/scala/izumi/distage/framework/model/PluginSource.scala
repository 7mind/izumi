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
  def apply(pluginConfig: PluginConfig): PluginSource = PluginSource(BootstrapConfig(pluginConfig, None))
  def apply(pluginConfig: PluginConfig, bootstrapPluginConfig: PluginConfig): PluginSource = PluginSource(BootstrapConfig(pluginConfig, Some(bootstrapPluginConfig)))
  def apply(pluginConfig: PluginConfig, bootstrapPluginConfig: Option[PluginConfig]): PluginSource = PluginSource(BootstrapConfig(pluginConfig, bootstrapPluginConfig))
  def apply(bootstrapConfig: BootstrapConfig): PluginSource = Load.withDefaultLoaders(bootstrapConfig)
  /** Load plugins using the specified loaders */
  def apply(pluginLoader: PluginLoader): PluginSource = Load(pluginLoader, PluginLoader.empty, None)
  def apply(pluginLoader: PluginLoader, bootstrapPluginLoader: PluginLoader): PluginSource = new Load(pluginLoader, bootstrapPluginLoader, None)
  /** Directly use the specified plugins */
  def apply(plugins: Seq[PluginBase]): PluginSource = PluginSource(plugins, Seq.empty)
  def apply(plugins: Seq[PluginBase], bootstrapPlugins: Seq[PluginBase]): PluginSource = PluginSource(AllLoadedPlugins(plugins, bootstrapPlugins))
  def apply(allLoadedPlugins: AllLoadedPlugins): PluginSource = Const(allLoadedPlugins)
  def empty: PluginSource = PluginSource(AllLoadedPlugins.empty)

  implicit final class PluginSourcesMerge(private val pluginSources: Iterable[PluginSource]) extends AnyVal {
    def merge: PluginSource = pluginSources.foldLeft(PluginSource.empty)(_ ++ _)
  }

  final case class Join(a: PluginSource, b: PluginSource) extends PluginSource {
    override def load(): AllLoadedPlugins = a.load() ++ b.load()
  }
  final case class Map(a: PluginSource, f: AllLoadedPlugins => AllLoadedPlugins) extends PluginSource {
    override def load(): AllLoadedPlugins = f(a.load())
  }
  final case class Load(pluginLoader: PluginLoader, bootstrapPluginLoader: PluginLoader, bootstrapConfig: Option[BootstrapConfig]) extends PluginSource {
    def load(): AllLoadedPlugins = {
      val bsPlugins = bootstrapPluginLoader.load()
      val appPlugins = pluginLoader.load()
      AllLoadedPlugins(appPlugins, bsPlugins)
    }
  }
  object Load {
    def withDefaultLoaders(bootstrapConfig: BootstrapConfig): PluginSource = {
      Load(PluginLoader(bootstrapConfig.pluginConfig), bootstrapConfig.bootstrapPluginConfig.fold(PluginLoader.empty)(PluginLoader(_)), Some(bootstrapConfig))
    }
  }

  trait Custom extends PluginSource

  final case class Const(plugins: AllLoadedPlugins) extends PluginSource.Custom {
    override def load(): AllLoadedPlugins = plugins
  }

  implicit final class ReplaceLoaders(private val pluginSource: PluginSource) extends AnyVal {
    def replaceLoaders(f: PluginSource.Load => PluginSource): PluginSource = {
      pluginSource match {
        case Join(a, b) => Join(a.replaceLoaders(f), b.replaceLoaders(f))
        case Map(a, g) => Map(a.replaceLoaders(f), g)
        case i: Load => f(i)
        case c: Custom => c
      }
    }
  }

}
