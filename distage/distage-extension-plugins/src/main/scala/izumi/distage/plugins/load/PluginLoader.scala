package izumi.distage.plugins.load

import izumi.distage.plugins.{PluginBase, PluginConfig}

trait PluginLoader {
  /** Will not scan if no packages are specified (add `"_root_"` package if you want to scan everything) */
  def load(config: PluginConfig): LoadedPlugins

  private[distage] def loadOwned(config: PluginConfig, owner: PluginPackageCache): LoadedPlugins = {
    val _ = owner
    load(config)
  }

  private[distage] final def withPackageCacheOwner(owner: PluginPackageCache): PluginLoader = new PluginLoader {
    override def load(config: PluginConfig): LoadedPlugins = PluginLoader.this.loadOwned(config, owner)
  }

  final def map(f: LoadedPlugins => LoadedPlugins): PluginLoader = new PluginLoader {
    override def load(config: PluginConfig): LoadedPlugins = f(PluginLoader.this.load(config))
    override private[distage] def loadOwned(config: PluginConfig, owner: PluginPackageCache): LoadedPlugins = f(PluginLoader.this.loadOwned(config, owner))
  }
}

object PluginLoader {
  /** Create a [[PluginLoader]] that scans the classpath according to [[PluginConfig]] */
  @inline def apply(): PluginLoader = new PluginLoaderDefaultImpl

  /** Create a [[PluginLoader]] that ignores [[PluginConfig]] and returns the specified plugins */
  def const(plugins: Seq[PluginBase]): PluginLoader = _ => LoadedPlugins(plugins, Nil, Nil)
  def const(plugin: PluginBase): PluginLoader = _ => LoadedPlugins(plugin :: Nil, Nil, Nil)
  def empty: PluginLoader = _ => LoadedPlugins.empty
}
