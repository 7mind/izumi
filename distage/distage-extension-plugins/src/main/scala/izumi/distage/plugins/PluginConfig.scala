package izumi.distage.plugins

import izumi.distage.plugins.StaticPluginLoader.StaticPluginLoaderMacro
import izumi.fundamentals.platform.language.SourcePackageMaterializer

import scala.language.experimental.macros

/** @see [[https://izumi.7mind.io/distage/distage-framework#plugins Plugins]] */
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

  def overriddenBy(plugins: Seq[PluginBase]): PluginConfig = copy(overrides = overrides ++ plugins)
  def overriddenBy(plugin: PluginBase): PluginConfig = copy(overrides = overrides ++ Seq(plugin))

  def cachePackages(cachePackages: Boolean): PluginConfig = copy(cachePackages = cachePackages)
  def debug(debug: Boolean): PluginConfig = copy(debug = debug)
}

object PluginConfig {
  /** Scan the specified package at runtime for classes and objects that inherit [[PluginBase]] */
  def cached(pluginsPackage: String): PluginConfig = PluginConfig(pluginsPackage :: Nil, Nil, cachePackages = cacheEnabled, debug = false, Nil, Nil)

  /** Scan the specified packages at runtime for classes and objects that inherit [[PluginBase]] */
  def cached(packagesEnabled: Seq[String]): PluginConfig = PluginConfig(packagesEnabled, Nil, cachePackages = cacheEnabled, debug = false, Nil, Nil)

  /** Scan the current source file's package at runtime for classes and objects that inherit [[PluginBase]] */
  def cachedThisPkg(implicit pkg: SourcePackageMaterializer): PluginConfig = cached(pkg.get.pkg)

  /** Scan the specified package at runtime for classes and objects that inherit [[PluginBase]], disabling plugin cache */
  def packages(pluginsPackage: String): PluginConfig = PluginConfig(pluginsPackage :: Nil, Nil, cachePackages = false, debug = false, Nil, Nil)
  def packages(packagesEnabled: Seq[String]): PluginConfig = PluginConfig(packagesEnabled, Nil, cachePackages = false, debug = false, Nil, Nil)
  def packagesThisPkg(implicit pkg: SourcePackageMaterializer): PluginConfig = packages(pkg.get.pkg)

  /** Scan the specified package *at compile-time* for classes and objects that inherit [[PluginBase]]
    *
    * WARN: may interact badly with incremental compilation
    * WARN: will _not_ find plugins defined in the current module, only those defined in dependency modules
    *       (similarly to how you cannot call Scala macros defined in the current module)
    */
  def compileTime(pluginsPackage: String): PluginConfig = macro StaticPluginLoaderMacro.scanCompileTimeConfig

  /** Scan the the current source file's package *at compile-time* for classes and objects that inherit [[PluginBase]]
    *
    * WARN: may interact badly with incremental compilation
    * WARN: will _not_ find plugins defined in the current module, only those defined in dependency modules
    *       (similarly to how you cannot call Scala macros defined in the current module)
    */
  def compileTimeThisPkg: PluginConfig = macro StaticPluginLoaderMacro.scanCompileTimeConfigThisPkg

  /** Create a [[PluginConfig]] that simply returns the specified plugins */
  def const(plugins: Seq[PluginBase]): PluginConfig = PluginConfig(Nil, Nil, cachePackages = false, debug = false, plugins, Nil)

  /** Create a [[PluginConfig]] that simply returns the specified plugin */
  def const(plugin: PluginBase): PluginConfig = const(Seq(plugin))

  /** A [[PluginConfig]] that returns no plugins */
  lazy val empty: PluginConfig = const(Nil)

  private[this] lazy val cacheEnabled: Boolean = DebugProperties.`izumi.distage.plugins.cache`.boolValue(true)
}
