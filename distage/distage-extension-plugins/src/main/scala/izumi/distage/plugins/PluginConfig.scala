package izumi.distage.plugins

final case class PluginConfig(
                               packagesEnabled: Seq[String],
                               packagesDisabled: Seq[String],
                               cachePackages: Boolean,
                               debug: Boolean,
                             )

object PluginConfig {
  def cached(packagesEnabled: Seq[String]): PluginConfig = PluginConfig(packagesEnabled, Nil, cachePackages = cacheEnabled, debug = false)
  def packages(packagesEnabled: Seq[String]): PluginConfig = PluginConfig(packagesEnabled, Nil, cachePackages = false, debug = false)
  lazy val empty: PluginConfig = PluginConfig(Nil, Nil, cachePackages = false, debug = false)

  private[this] lazy val cacheEnabled: Boolean = {
    import izumi.fundamentals.platform.strings.IzString._
    System
      .getProperty(DebugProperties.`izumi.distage.plugins.cache`)
      .asBoolean(true)
  }
}
