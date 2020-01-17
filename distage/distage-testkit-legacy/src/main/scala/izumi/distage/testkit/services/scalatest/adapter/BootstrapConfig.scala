package izumi.distage.testkit.services.scalatest.adapter

import izumi.distage.plugins.PluginConfig

@deprecated("Use dstest", "2019/Jul/18")
final case class BootstrapConfig(
                                  pluginConfig: PluginConfig,
                                  bootstrapPluginConfig: Option[PluginConfig],
                                )
