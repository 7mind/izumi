package izumi.distage.testkit.distagesuite.generic

import izumi.distage.plugins.{PluginBase, PluginConfig}
import izumi.distage.testkit.distagesuite.fixtures.*

object DistageMemoizeExamplePlatformSpecific {
  def pluginConfigForFixturesPkg: PluginConfig = {
    PluginConfig.const(
      Seq[PluginBase](
        MockAppCatsIOPlugin,
        MockAppZioPlugin,
        MockAppIdPlugin,
        MockAppZioZEnvPlugin,
      )
    )
  }
}
