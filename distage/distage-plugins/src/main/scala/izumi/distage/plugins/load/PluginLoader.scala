package izumi.distage.plugins.load

import izumi.distage.plugins.PluginBase

trait PluginLoader {
  def load(): Seq[PluginBase]
}

object PluginLoader {
  /** Create a [[PluginLoader]] that scans the classpath according to [[PluginConfig]] */
  def apply(pluginConfig: PluginConfig): PluginLoader = new PluginLoaderDefaultImpl(pluginConfig)

  /** Create a [[PluginLoader]] that returns the specified plugins */
  def apply(plugins: Seq[PluginBase]): PluginLoader = () => plugins
  def apply(plugins: PluginBase): PluginLoader = apply(Seq(plugins))

  lazy val empty: PluginLoader = () => Nil

  final case class PluginConfig(
                                 debug: Boolean,
                                 packagesEnabled: Seq[String],
                                 packagesDisabled: Seq[String] = Nil,
                               )
  object PluginConfig {
    def empty: PluginConfig = PluginConfig(false, Nil, Nil)
  }

}
