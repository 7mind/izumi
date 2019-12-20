package izumi.distage.testkit.services.dstest

import distage.DIKey
import izumi.distage.framework.model.{ActivationInfo, PluginSource}
import izumi.distage.framework.services.ActivationInfoExtractor
import izumi.distage.model.definition.{Activation, BootstrapModule, ModuleBase}
import izumi.distage.plugins.merge.{PluginMergeStrategy, SimplePluginMergeStrategy}
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.testkit.DebugProperties
import izumi.distage.testkit.services.PluginsCache
import izumi.fundamentals.platform.language.unused
import izumi.logstage.api.IzLogger

trait TestEnvironmentProvider {
  /** Merge strategy will be applied only once for all the tests with the same BootstrapConfig, if memoization is on */
  def loadEnvironment(logger: IzLogger): TestEnvironment
}

object TestEnvironmentProvider {

  class Impl
  (
    protected val pluginSource: PluginSource,
    protected val activation: Activation,
    protected val memoizedKeys: Set[DIKey],
    protected val bootstrapOverrides: BootstrapModule,
    protected val moduleOverrides: ModuleBase,
  ) extends TestEnvironmentProvider {

    override final def loadEnvironment(logger: IzLogger): TestEnvironment = {
      val cachedSource = if (memoizePlugins) {
        PluginsCache.cachePluginSource(pluginSource)
      } else pluginSource

      val plugins = cachedSource.load()
      val mergeStrategy = makeMergeStrategy(logger)
      val appModule = mergeStrategy.merge(plugins.app)
      val bootstrapModule = mergeStrategy.merge(plugins.bootstrap)
      val availableActivations = ActivationInfoExtractor.findAvailableChoices(logger, appModule)

      doLoad(logger, appModule, bootstrapModule, availableActivations)
    }

    protected final def doLoad(logger: IzLogger, appModule: ModuleBase, bootstrapModule: ModuleBase, availableActivations: ActivationInfo): TestEnvironment = {
      val roles = loadRoles(logger)
      TestEnvironment(
        bsModule = bootstrapModule overridenBy bootstrapOverrides,
        appModule = appModule overridenBy moduleOverrides,
        roles = roles,
        activationInfo = availableActivations,
        activation = activation,
        memoizedKeys = memoizedKeys,
      )
    }

    protected def memoizePlugins: Boolean = {
      import izumi.fundamentals.platform.strings.IzString._

      System
        .getProperty(DebugProperties.`izumi.distage.testkit.plugins.memoize`)
        .asBoolean(true)
    }

    protected def loadRoles(@unused logger: IzLogger): RolesInfo = {
      // For all normal scenarios we don't need roles to setup a test
      RolesInfo(Set.empty, Seq.empty, Seq.empty, Seq.empty, Set.empty)
    }

    protected def makeMergeStrategy(@unused lateLogger: IzLogger): PluginMergeStrategy = {
      SimplePluginMergeStrategy
    }

  }

}
