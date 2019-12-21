package izumi.distage.testkit.services

import izumi.distage.framework.model.PluginSource.{Custom, Join, Load, Map}
import izumi.distage.framework.model.{ActivationInfo, BootstrapConfig, PluginSource}
import izumi.distage.model.definition.ModuleBase

object PluginsCache {
  final case class CacheKey(config: Set[BootstrapConfig])
  final case class CacheValue(
                               mergedAppPlugins: ModuleBase,
                               mergedBsPlugins: ModuleBase,
                               availableActivations: ActivationInfo,
                             )

  // sbt in nofork mode runs each module in it's own classloader thus we have separate cache per module per run
  object Instance extends SyncCache[CacheKey, CacheValue]

  def collectBsConfigs(pluginSource: PluginSource): Set[BootstrapConfig] = {
    val bsConfigs = Set.newBuilder[BootstrapConfig]

    def process(pluginSource: PluginSource): Unit = {
      pluginSource match {
        case Join(a, b) =>
          process(a)
          process(b)
        case Map(a, _) =>
          process(a)
        case Load(_, _, Some(bootstrapConfig)) =>
          bsConfigs += bootstrapConfig
        case _: Load | _: Custom =>
      }
    }
    process(pluginSource)
    bsConfigs.result()
  }

}
