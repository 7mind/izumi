package izumi.distage.plugins

import scala.language.experimental.macros

final case class PluginConfig(
  packagesEnabled: Seq[String],
  packagesDisabled: Seq[String],
  cachePackages: Boolean,
  debug: Boolean,
  merges: Seq[PluginBase],
  overrides: Seq[PluginBase],
) {
  def enablePackages(packagesEnabled: Seq[String]): PluginConfig = copy(packagesEnabled = this.packagesEnabled ++ packagesEnabled)
  def enablePackage(packageEnabled: String): PluginConfig = enablePackages(Seq(packageEnabled))

  def disablePackages(packagesDisabled: Seq[String]): PluginConfig = copy(packagesDisabled = this.packagesDisabled ++ packagesDisabled)
  def disablePackage(packageDisabled: String): PluginConfig = disablePackages(Seq(packageDisabled))

  def ++(plugins: Seq[PluginBase]): PluginConfig = copy(merges = merges ++ plugins)
  def ++(plugin: PluginBase): PluginConfig = copy(merges = merges ++ Seq(plugin))

  def overridenBy(plugins: Seq[PluginBase]): PluginConfig = copy(overrides = overrides ++ plugins)
  def overridenBy(plugin: PluginBase): PluginConfig = copy(overrides = overrides ++ Seq(plugin))

  def cachePackages(cachePackages: Boolean): PluginConfig = copy(cachePackages = cachePackages)
  def debug(debug: Boolean): PluginConfig = copy(debug = debug)
}

object PluginConfig {
  /** Scan the specified packages for modules that inherit [[PluginBase]] */
  def cached(packagesEnabled: Seq[String]): PluginConfig = PluginConfig(packagesEnabled, Nil, cachePackages = cacheEnabled, debug = false, Nil, Nil)
  def cached(pluginsPackage: String): PluginConfig = PluginConfig(pluginsPackage :: Nil, Nil, cachePackages = cacheEnabled, debug = false, Nil, Nil)
  def packages(packagesEnabled: Seq[String]): PluginConfig = PluginConfig(packagesEnabled, Nil, cachePackages = false, debug = false, Nil, Nil)
  def packages(pluginsPackage: String): PluginConfig = PluginConfig(pluginsPackage :: Nil, Nil, cachePackages = false, debug = false, Nil, Nil)

  /** Create a [[PluginConfig]] that returns a list of plugins scanned at compile-time.
    *
    * WARN: may interact badly with incremental compilation
    * WARN: will _not_ find plugins defined in current module, only those defined in dependency modules (similarly to
    *       how you cannot call Scala macros defined in the current module)
    */
  def staticallyAvailablePlugins(pluginsPackage: String): PluginConfig = macro StaticPluginScannerMacro.staticallyAvailablePluginConfig

  /** Create a [[PluginConfig]] that simply returns the specified plugins */
  def const(plugins: Seq[PluginBase]): PluginConfig = PluginConfig(Nil, Nil, cachePackages = false, debug = false, plugins, Nil)
  def const(plugin: PluginBase): PluginConfig = const(Seq(plugin))

  /** A [[PluginConfig]] that returns no plugins */
  lazy val empty: PluginConfig = const(Nil)

  private[this] lazy val cacheEnabled: Boolean = DebugProperties.`izumi.distage.plugins.cache`.boolValue(true)
}
