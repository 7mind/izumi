package com.github.pshirshov.izumi.distage.plugins.load

import com.github.pshirshov.izumi.distage.plugins.PluginBase

trait PluginLoader {
  def load(): Seq[PluginBase]
}

object PluginLoader {
  /**
    * Create a [[PluginLoader]] that scans classpath according to [[PluginConfig]]
    * */
  def apply(pluginConfig: PluginConfig): PluginLoader = new PluginLoaderDefaultImpl(pluginConfig)

  /**
    * Create a [[PluginLoader]] that simply returns specified plugins
    */
  def apply(plugins: Seq[PluginBase]): PluginLoader = new PluginLoaderPredefImpl(plugins)

  def empty: PluginLoader = PluginLoaderNullImpl

  final case class PluginConfig
  (
    debug: Boolean
    , packagesEnabled: Seq[String]
    , packagesDisabled: Seq[String]
  )
}
