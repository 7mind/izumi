package izumi.distage.testkit.distagesuite.generic

import izumi.distage.plugins.PluginConfig
import izumi.distage.testkit.distagesuite.fixtures.*

object DistageMemoizeExamplePlatformSpecific {
  def pluginConfigForFixturesPkg: PluginConfig = {
    PluginConfig.const(
      Seq(
        MockAppCatsIOPlugin,
        MockAppZioPlugin,
        MockAppIdPlugin,
        MockAppZioZEnvPlugin,
      )
    )
  }
}
