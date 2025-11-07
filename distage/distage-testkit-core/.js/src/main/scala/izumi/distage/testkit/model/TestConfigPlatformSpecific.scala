package izumi.distage.testkit.model

import izumi.distage.plugins.PluginConfig

import scala.annotation.unused

trait TestConfigPlatformSpecific {
  @deprecated("Use TestConfig() constructor instead, always provide pluginConfig explicitly", "1.2.3")
  /** runtime plugin discovery is not available on Scala.js (although you can use [[PluginConfig.compileTimeThisPkg]]) */
  def forSuite(@unused clazz: Class[?]): TestConfig = {
    TestConfig(
      pluginConfig = PluginConfig.empty
    )
  }
}
