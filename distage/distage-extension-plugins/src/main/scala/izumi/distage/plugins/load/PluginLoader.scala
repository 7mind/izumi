package izumi.distage.plugins.load

import izumi.distage.plugins.{PluginBase, PluginConfig}

trait PluginLoader {
  def load(config: PluginConfig): LoadedPlugins

  final def map(f: LoadedPlugins => LoadedPlugins): PluginLoader = c => f(load(c))
}

object PluginLoader {
  /** Create a [[PluginLoader]] that scans the classpath according to [[PluginConfig]] */
  @inline def apply(): PluginLoader = new PluginLoaderDefaultImpl

  /** Create a [[PluginLoader]] that ignores [[PluginConfig]] and returns the specified plugins */
  def const(plugins: Seq[PluginBase]): PluginLoader = _ => LoadedPlugins(plugins, Nil, Nil)
  def const(plugin: PluginBase): PluginLoader = _ => LoadedPlugins(plugin :: Nil, Nil, Nil)
  def empty: PluginLoader = _ => LoadedPlugins.empty
}
