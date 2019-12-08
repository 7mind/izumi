package izumi.distage.testkit.services.dstest

import distage.DIKey
import izumi.distage.framework.model.PluginSource
import izumi.distage.framework.services.ActivationInfoExtractor
import izumi.distage.model.definition.{Activation, BootstrapModule, ModuleBase}
import izumi.distage.plugins.merge.{PluginMergeStrategy, SimplePluginMergeStrategy}
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.testkit.services.PluginsCache
import izumi.distage.testkit.services.PluginsCache.{CacheKey, CacheValue}
import izumi.fundamentals.platform.language.Quirks
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

    /**
      * Merge strategy will be applied only once for all the tests with the same bootstrap config when memoization is on
      */
    override final def loadEnvironment(logger: IzLogger): TestEnvironment = {
      val bootstrapConfig = pluginSource.bootstrapConfig
      def env(): CacheValue = {
        val plugins = pluginSource.load()
        val mergeStrategy = makeMergeStrategy(logger)
        val defApp = mergeStrategy.merge(plugins.app)
        val bootstrap = mergeStrategy.merge(plugins.bootstrap)
        val availableActivations = ActivationInfoExtractor.findAvailableChoices(logger, defApp)
        CacheValue(plugins, bootstrap, defApp, availableActivations)
      }

      val plugins = bootstrapConfig match {
        case Some(config) if memoizePlugins =>
          PluginsCache.Instance.getOrCompute(CacheKey(config), env())
        case _ =>
          env()
      }

      doLoad(logger, plugins)
    }

    protected final def doLoad(logger: IzLogger, env: CacheValue): TestEnvironment = {
      val roles = loadRoles(logger)
      TestEnvironment(
        baseBsModule = env.bsModule overridenBy bootstrapOverrides,
        appModule = env.appModule overridenBy moduleOverrides,
        roles = roles,
        activationInfo = env.availableActivations,
        activation = activation,
        memoizedKeys = memoizedKeys,
      )
    }

    protected def memoizePlugins: Boolean = {
      import izumi.fundamentals.platform.strings.IzString._

      System.getProperty("izumi.distage.testkit.plugins.memoize")
        .asBoolean(true)
    }

    protected def loadRoles(logger: IzLogger): RolesInfo = {
      Quirks.discard(logger)
      // For all normal scenarios we don't need roles to setup a test
      RolesInfo(Set.empty, Seq.empty, Seq.empty, Seq.empty, Set.empty)
    }

    protected def makeMergeStrategy(lateLogger: IzLogger): PluginMergeStrategy = {
      Quirks.discard(lateLogger)
      SimplePluginMergeStrategy
    }

  }

}
