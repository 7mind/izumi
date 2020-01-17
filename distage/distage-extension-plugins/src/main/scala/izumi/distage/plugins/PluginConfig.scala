package izumi.distage.plugins

final case class PluginLoadConfig(
                                   packagesEnabled: Seq[String],
                                   packagesDisabled: Seq[String],
                                   cachePackages: Boolean,
                                   debug: Boolean,
                                 )
object PluginLoadConfig {
  def cached(packagesEnabled: Seq[String]): PluginLoadConfig = PluginLoadConfig(packagesEnabled, Nil, cachePackages = cacheEnabled, debug = false)
  def packages(packagesEnabled: Seq[String]): PluginLoadConfig = PluginLoadConfig(packagesEnabled, Nil, cachePackages = false, debug = false)
  lazy val initial: Option[PluginLoadConfig] = Some(cached(Nil))

  private[this] lazy val cacheEnabled: Boolean = {
    import izumi.fundamentals.platform.strings.IzString._
    System
      .getProperty(DebugProperties.`izumi.distage.plugins.cache`)
      .asBoolean(true)
  }
}

final case class PluginConfig(
                               loadConfig: Option[PluginLoadConfig],
                               merges: Seq[PluginBase],
                               overrides: Seq[PluginBase],
                             ) {
  def enablePackages(packagesEnabled: Seq[String]): PluginConfig = modifyLoadConfig(c => c.copy(packagesEnabled = c.packagesEnabled ++ packagesEnabled))
  def enablePackage(packageEnabled: String): PluginConfig = enablePackages(Seq(packageEnabled))

  def disablePackages(packagesDisabled: Seq[String]): PluginConfig = modifyLoadConfig(c => c.copy(packagesDisabled = c.packagesDisabled ++ packagesDisabled))
  def disablePackage(packageDisabled: String): PluginConfig = disablePackages(Seq(packageDisabled))

  def ++(plugins: Seq[PluginBase]): PluginConfig = copy(merges = merges ++ plugins)
  def ++(plugin: PluginBase): PluginConfig = copy(merges = merges ++ Seq(plugin))

  def overridenBy(plugins: Seq[PluginBase]): PluginConfig = copy(overrides = overrides ++ plugins)
  def overridenBy(plugin: PluginBase): PluginConfig = copy(overrides = overrides ++ Seq(plugin))

  def cachePackages(cachePackages: Boolean): PluginConfig = copy(loadConfig = loadConfig.map(_.copy(cachePackages = cachePackages)))
  def debug(debug: Boolean): PluginConfig = copy(loadConfig = loadConfig.map(_.copy(debug = debug)))

  @inline private[this] def modifyLoadConfig(f: PluginLoadConfig => PluginLoadConfig): PluginConfig = {
    copy(loadConfig = Some(f(loadConfig.getOrElse(PluginLoadConfig.initial))))
  }
}

object PluginConfig {
  /** Scan the specified packages for modules that inherit [[PluginBase]] */
  def cached(packagesEnabled: Seq[String]): PluginConfig = PluginConfig(Some(PluginLoadConfig.cached(packagesEnabled)), Nil, Nil)
  def packages(packagesEnabled: Seq[String]): PluginConfig = PluginConfig(Some(PluginLoadConfig.packages(packagesEnabled)), Nil, Nil)

  /** Create a [[PluginConfig]] that simply returns the specified plugins */
  def const(plugins: Seq[PluginBase]): PluginConfig = PluginConfig(None, plugins, Nil)
  def const(plugin: PluginBase): PluginConfig = const(Seq(plugin))
  lazy val empty: PluginConfig = const(Nil)
}
