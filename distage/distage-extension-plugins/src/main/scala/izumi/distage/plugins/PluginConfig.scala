package izumi.distage.plugins

final case class PluginConfig(
                               packagesEnabled: Seq[String],
                               packagesDisabled: Seq[String],
                               cachePackages: Boolean,
                               debug: Boolean,
                             )
object PluginConfig {
  def empty: PluginConfig = PluginConfig(Nil, Nil, cachePackages = false, debug = false)
  def packages(packagesEnabled: Seq[String]): PluginConfig = PluginConfig(packagesEnabled, Nil, cachePackages = true, debug = false)
  def cached(packagesEnabled: Seq[String]): PluginConfig = {
    import izumi.fundamentals.platform.strings.IzString._
    val cacheEnabled = System
      .getProperty(DebugProperties.`izumi.distage.testkit.plugins.memoize`)
      .asBoolean(true)
    PluginConfig(packagesEnabled, Nil, cachePackages = cacheEnabled, debug = false)
  }
}
