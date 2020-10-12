package izumi.distage.roles.launcher

import distage.ModuleBase
import izumi.distage.plugins.PluginBase
import izumi.distage.plugins.load.LoadedPlugins
import izumi.distage.plugins.merge.PluginMergeStrategy
import izumi.distage.roles.launcher.ModuleValidator.ValidatedModulePair
import izumi.distage.roles.model.exceptions.DIAppBootstrapException
import izumi.logstage.api.IzLogger
import logstage.Info

trait ModuleValidator {
  def validate(strategy: PluginMergeStrategy, plugins: LoadedPlugins, bsStrategy: PluginMergeStrategy, bsPlugins: LoadedPlugins): ValidatedModulePair
}

object ModuleValidator {

  final case class ValidatedModulePair(
    bootstrapAutoModule: ModuleBase,
    appModule: ModuleBase,
  )

  final class ModuleValidatorImpl(
    logger: IzLogger
  ) extends ModuleValidator {
    override def validate(
      strategy: PluginMergeStrategy,
      plugins: LoadedPlugins,
      bsStrategy: PluginMergeStrategy,
      bsPlugins: LoadedPlugins,
    ): ValidatedModulePair = {
      val appModule = strategy.merge(plugins.result)
      val bootstrapAutoModule = bsStrategy.merge(bsPlugins.result)

      val conflicts = bootstrapAutoModule.keys.intersect(appModule.keys)
      if (conflicts.nonEmpty)
        throw new DIAppBootstrapException(
          s"Same keys defined by bootstrap and app plugins: $conflicts. Most likely your bootstrap configs are contradictive, terminating..."
        )
      if (appModule.bindings.isEmpty) {
        throw new DIAppBootstrapException("Empty app object graph. Most likely you have no plugins defined or your app plugin config is wrong, terminating...")
      }

      logger.log(Info) {
        val appCount = appModule.bindings.size
        val bsCount = bootstrapAutoModule.bindings.size
        s"Available ${plugins.size -> "app plugins"} with ${appCount -> "app bindings"} and ${bsPlugins.size -> "bootstrap plugins"} with ${bsCount -> "bootstrap bindings"} ..."
      }

      ValidatedModulePair(bootstrapAutoModule, appModule)
    }
  }

}
