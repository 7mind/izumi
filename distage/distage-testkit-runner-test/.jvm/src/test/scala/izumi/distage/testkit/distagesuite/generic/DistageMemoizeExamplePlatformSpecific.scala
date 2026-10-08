package izumi.distage.testkit.distagesuite.generic

import izumi.distage.plugins.PluginConfig

object DistageMemoizeExamplePlatformSpecific {
  def pluginConfigForFixturesPkg: PluginConfig = {
    PluginConfig.cached("izumi.distage.testkit.distagesuite.fixtures")
  }
}
