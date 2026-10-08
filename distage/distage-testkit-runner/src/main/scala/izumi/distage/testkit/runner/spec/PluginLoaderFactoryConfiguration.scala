package izumi.distage.testkit.runner.spec

import izumi.distage.plugins.load.PluginLoaderFactory

trait PluginLoaderFactoryConfiguration {
  protected def makePluginLoaderFactory(): PluginLoaderFactory
}
