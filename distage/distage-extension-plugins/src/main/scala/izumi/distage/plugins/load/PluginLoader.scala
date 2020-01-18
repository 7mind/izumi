package izumi.distage.plugins.load

import izumi.distage.plugins.{PluginBase, PluginConfig}

trait PluginLoader {
  def load(config: PluginConfig): Seq[PluginBase]

  final def map(f: Seq[PluginBase] => Seq[PluginBase]): PluginLoader = c => f(load(c))
}

object PluginLoader {
  /** Create a [[PluginLoader]] that scans the classpath according to [[PluginConfig]] */
  def apply(): PluginLoader = new PluginLoaderDefaultImpl

  /** Create a [[PluginLoader]] that ignores [[PluginConfig]] and returns the specified plugins */
  def const(plugins: Seq[PluginBase]): PluginLoader = _ => plugins
  def const(plugin: PluginBase): PluginLoader = _ => Seq(plugin)
  lazy val empty: PluginLoader = const(Nil)
}
