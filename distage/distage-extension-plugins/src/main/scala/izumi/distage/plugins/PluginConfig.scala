package izumi.distage.plugins

import izumi.distage.model.definition.ModuleBase
import izumi.fundamentals.platform.language.SourcePackageMaterializer

/** @see [[https://izumi.7mind.io/distage/distage-framework#plugins Plugins]] */
final case class PluginConfig(
  packagesEnabled: Seq[String],
  packagesDisabled: Seq[String],
  cachePackages: Boolean,
  debug: Boolean,
  merges: Seq[ModuleBase],
  overrides: Seq[ModuleBase],
) {
  def enablePackages(packagesEnabled: Seq[String]): PluginConfig = copy(packagesEnabled = this.packagesEnabled ++ packagesEnabled)
  def enablePackage(packageEnabled: String): PluginConfig = enablePackages(Seq(packageEnabled))

  def disablePackages(packagesDisabled: Seq[String]): PluginConfig = copy(packagesDisabled = this.packagesDisabled ++ packagesDisabled)
  def disablePackage(packageDisabled: String): PluginConfig = disablePackages(Seq(packageDisabled))

  def ++(plugins: Seq[ModuleBase]): PluginConfig = copy(merges = merges ++ plugins)
  def ++(plugin: ModuleBase): PluginConfig = copy(merges = merges ++ Seq(plugin))

  def overriddenBy(plugins: Seq[ModuleBase]): PluginConfig = copy(overrides = overrides ++ plugins)
  def overriddenBy(plugin: ModuleBase): PluginConfig = copy(overrides = overrides ++ Seq(plugin))

  def cachePackages(cachePackages: Boolean): PluginConfig = copy(cachePackages = cachePackages)
  def debug(debug: Boolean): PluginConfig = copy(debug = debug)
}

object PluginConfig extends PluginConfigStatic {
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

  /** Create a [[PluginConfig]] that simply contains the specified plugins */
  def const(plugins: Seq[PluginBase]): PluginConfig = PluginConfig(Nil, Nil, cachePackages = false, debug = false, plugins, Nil)

  /** Create a [[PluginConfig]] that simply contains the specified plugin */
  def const(plugin: PluginBase): PluginConfig = const(Seq(plugin))

  /**
    * Like [[const]], but accepts simple [[ModuleBase]].
    * Unlike for inheritors of [[PluginDef]], changing a ModuleDef source code may not trigger recompilation of compile-time checks
    */
  def constUnchecked(modules: Seq[ModuleBase]): PluginConfig = PluginConfig(Nil, Nil, cachePackages = false, debug = false, modules, Nil)

  /**
    * Like [[const]], but accepts simple [[ModuleBase]].
    * Unlike for inheritors of [[PluginDef]], changing a ModuleDef source code may not trigger recompilation of compile-time checks
    */
  def constUnchecked(module: ModuleBase): PluginConfig = constUnchecked(Seq(module))

  /** A [[PluginConfig]] that returns no plugins */
  lazy val empty: PluginConfig = const(Nil)

  private[this] lazy val cacheEnabled: Boolean = DebugProperties.`izumi.distage.plugins.cache`.boolValue(true)
}
