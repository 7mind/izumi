package izumi.distage.plugins.load

import izumi.distage.plugins.{PluginBase, PluginConfig}

trait PluginLoader {
  def load(): Seq[PluginBase]

  final def map(f: Seq[PluginBase] => Seq[PluginBase]): PluginLoader = () => f(load())
  final def ++(that: PluginLoader): PluginLoader = () => load() ++ that.load()

  final def ++(plugins: Seq[PluginBase]): PluginLoader = map(_ ++ plugins)
  final def ++(plugin: PluginBase): PluginLoader = map(_ :+ plugin)

  final def overridenBy(plugins: Seq[PluginBase]): PluginLoader = map(ps => Seq(ps.merge overridenBy plugins.merge))
  final def overridenBy(plugin: PluginBase): PluginLoader = map(ps => Seq(ps.merge overridenBy plugin))
}

object PluginLoader {
  /** Create a [[PluginLoader]] that scans the specified packages */
  def cached(packagesEnabled: Seq[String]): PluginLoader = PluginLoader(PluginConfig.cached(packagesEnabled))
  def packages(packagesEnabled: Seq[String]): PluginLoader = PluginLoader(PluginConfig.cached(packagesEnabled))

  /** Create a [[PluginLoader]] that scans the classpath according to [[PluginConfig]] */
  def apply(pluginConfig: PluginConfig): PluginLoader = new PluginLoaderDefaultImpl(pluginConfig)

  /** Create a [[PluginLoader]] that returns the specified plugins */
  def const(plugins: Seq[PluginBase]): PluginLoader = () => plugins
  def const(plugin: PluginBase): PluginLoader = () => Seq(plugin)

  lazy val empty: PluginLoader = () => Nil
}
