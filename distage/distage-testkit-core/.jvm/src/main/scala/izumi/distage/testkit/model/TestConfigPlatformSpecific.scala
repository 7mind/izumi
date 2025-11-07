package izumi.distage.testkit.model

import izumi.distage.plugins.PluginConfig

trait TestConfigPlatformSpecific {
  @deprecated("Use TestConfig() constructor instead, always provide pluginConfig explicitly", "1.2.3")
  def forSuite(clazz: Class[?]): TestConfig = {
    val packageName = clazz.getPackage.getName

    TestConfig(
      pluginConfig = PluginConfig.cached(Seq(packageName))
    )
  }
}
