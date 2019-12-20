package izumi.distage.plugins.load

import distage.plugins.PluginConfig
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
  def empty: PluginLoader = () => Nil
}
