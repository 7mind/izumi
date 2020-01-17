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
  def cached(packagesEnabled: Seq[String]): PluginLoader.Configurable = PluginLoader(PluginConfig.cached(packagesEnabled))
  def packages(packagesEnabled: Seq[String]): PluginLoader.Configurable = PluginLoader(PluginConfig.cached(packagesEnabled))

  /** Create a [[PluginLoader]] that scans the classpath according to [[PluginConfig]] */
  def apply(pluginConfig: PluginConfig): PluginLoader.Configurable = PluginLoader.Configurable.Impl(pluginConfig)

  /** Create a [[PluginLoader]] that returns the specified plugins */
  def const(plugins: Seq[PluginBase]): PluginLoader = () => plugins
  def const(plugin: PluginBase): PluginLoader = () => Seq(plugin)

  lazy val empty: PluginLoader = () => Nil

  trait Configurable extends PluginLoader {
    final def enablePackages(packagesEnabled: Seq[String]): Configurable = configure(c => c.copy(packagesEnabled = c.packagesEnabled ++ packagesEnabled))
    final def disablePackages(packagesDisabled: Seq[String]): Configurable = configure(c => c.copy(packagesDisabled = c.packagesDisabled ++ packagesDisabled))
    final def cachePackages(cachePackages: Boolean): Configurable = configure(_.copy(cachePackages = cachePackages))
    final def debug(debug: Boolean): Configurable = configure(_.copy(debug = debug))

    @inline final def configure(f: PluginConfig => PluginConfig): Configurable = copy(pluginConfig = f(pluginConfig))

    def copy(pluginConfig: PluginConfig = pluginConfig): Configurable
    def pluginConfig: PluginConfig
  }
  private object Configurable {
    final case class Impl(pluginConfig: PluginConfig) extends Configurable {
      override def load(): Seq[PluginBase] = new PluginLoaderDefaultImpl(pluginConfig).load()
      override def copy(pluginConfig: PluginConfig = pluginConfig): Impl = Impl(pluginConfig)
    }
  }
}
