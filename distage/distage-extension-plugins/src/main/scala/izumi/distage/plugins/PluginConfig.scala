package izumi.distage.plugins

final case class PluginConfig(
                               debug: Boolean = false,
                               packagesEnabled: Seq[String],
                               packagesDisabled: Seq[String] = Nil,
                             )
object PluginConfig {
  def empty: PluginConfig = PluginConfig(debug = false, Nil, Nil)
}
