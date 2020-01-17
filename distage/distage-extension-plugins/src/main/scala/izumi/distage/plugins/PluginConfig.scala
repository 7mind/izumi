package izumi.distage.plugins

import scala.collection.immutable.Queue

final case class PluginConfig(
                               loads: Seq[PluginLoadConfig],
                               merges: Seq[PluginBase],
                               overrides: Seq[PluginBase],
                             ) {
  def load(loadCfg: PluginLoadConfig): PluginConfig = copy(loads = loads :+ loadCfg)
  def loadPackages(pkgs: Seq[String]): PluginConfig = load(PluginLoadConfig.cached(pkgs))
  def loadPackage(pkg: String): PluginConfig = load(PluginLoadConfig.cached(Seq(pkg)))

  def ++(plugins: Seq[PluginBase]): PluginConfig = copy(merges = merges ++ plugins)
  def ++(plugin: PluginBase): PluginConfig = copy(merges = merges ++ Seq(plugin))

  def overridenBy(plugins: Seq[PluginBase]): PluginConfig = copy(overrides = overrides ++ plugins)
  def overridenBy(plugin: PluginBase): PluginConfig = copy(overrides = overrides ++ Seq(plugin))
}

object PluginConfig {
  /** Scan the specified packages for modules that inherit [[PluginBase]] */
  def cached(packagesEnabled: Seq[String]): PluginConfig = PluginConfig(Queue(PluginLoadConfig.cached(packagesEnabled)), Nil, Nil)
  def packages(packagesEnabled: Seq[String]): PluginConfig = PluginConfig(Queue(PluginLoadConfig.packages(packagesEnabled)), Nil, Nil)

  /** Create a [[PluginConfig]] that simply returns the specified plugins */
  def const(plugins: Seq[PluginBase]): PluginConfig = PluginConfig(Queue.empty, plugins, Nil)
  def const(plugin: PluginBase): PluginConfig = const(Seq(plugin))
  lazy val empty: PluginConfig = const(Nil)
}

final case class PluginLoadConfig(
                                   packagesEnabled: Seq[String],
                                   packagesDisabled: Seq[String],
                                   cachePackages: Boolean,
                                   debug: Boolean,
                                 )
object PluginLoadConfig {
  def cached(packagesEnabled: Seq[String]): PluginLoadConfig = PluginLoadConfig(packagesEnabled, Nil, cachePackages = cacheEnabled, debug = false)
  def packages(packagesEnabled: Seq[String]): PluginLoadConfig = PluginLoadConfig(packagesEnabled, Nil, cachePackages = false, debug = false)

  private[this] lazy val cacheEnabled: Boolean = {
    import izumi.fundamentals.platform.strings.IzString._
    System
      .getProperty(DebugProperties.`izumi.distage.plugins.cache`)
      .asBoolean(true)
  }
}
