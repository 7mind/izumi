package izumi.distage.testkit.services

import izumi.distage.framework.model.{AllLoadedPlugins, BootstrapConfig}
import izumi.distage.model.definition.ModuleBase
import izumi.distage.roles.model.ActivationInfo

object PluginsCache {
  final case class CacheKey(config: BootstrapConfig)
  final case class CacheValue(
                               plugins: AllLoadedPlugins,
                               bsModule: ModuleBase,
                               appModule: ModuleBase,
                               availableActivations: ActivationInfo,
                             )

  // sbt in nofork mode runs each module in it's own classloader thus we have separate cache per module per run
  object Instance extends SyncCache[CacheKey, CacheValue]
}
