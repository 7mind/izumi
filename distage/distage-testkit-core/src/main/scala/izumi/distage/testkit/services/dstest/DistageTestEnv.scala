package izumi.distage.testkit.services.dstest

import distage.plugins.PluginLoader
import izumi.distage.framework.activation.PruningPlanMergingPolicyLoggedImpl
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.framework.services.ActivationInfoExtractor
import izumi.distage.model.definition.BootstrapModuleDef
import izumi.distage.model.planning.PlanMergingPolicy
import izumi.distage.plugins.load.PluginLoaderDefaultImpl
import izumi.distage.plugins.merge.{PluginMergeStrategy, SimplePluginMergeStrategy}
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.testkit.{DebugProperties, TestConfig}
import izumi.fundamentals.platform.cache.SyncCache
import izumi.fundamentals.platform.language.unused
import izumi.logstage.api.IzLogger

trait DistageTestEnv {
  private[distage] def loadEnvironment(logger: IzLogger, testConfig: TestConfig): TestEnvironment = {
    val roles = loadRoles(logger)
    val mergeStrategy = makeMergeStrategy(logger)
    val pluginLoader = makePluginloader(logger)
    def doMake(): TestEnvironment = {
      makeEnv(logger, testConfig, pluginLoader, roles, mergeStrategy)
    }

    if (DebugProperties.`izumi.distage.testkit.environment.cache`.asBoolean(true)) {
      DistageTestEnv.cache.getOrCompute(DistageTestEnv.EnvCacheKey(testConfig, roles, mergeStrategy), doMake())
    } else {
      doMake()
    }
  }

  private[distage] def makeEnv(logger: IzLogger, testConfig: TestConfig, pluginLoader: PluginLoader, roles: RolesInfo, mergeStrategy: PluginMergeStrategy): TestEnvironment = {
    val appPlugins = pluginLoader.load(testConfig.pluginConfig)
    val bsPlugins = pluginLoader.load(testConfig.bootstrapPluginConfig)
    val appModule = mergeStrategy.merge(appPlugins) overridenBy testConfig.moduleOverrides
    val bootstrapModule = mergeStrategy.merge(bsPlugins) overridenBy testConfig.bootstrapOverrides
    val availableActivations = ActivationInfoExtractor.findAvailableChoices(logger, appModule)
    val activation = testConfig.activation

    val bsModule = bootstrapModule overridenBy new BootstrapModuleDef {
      make[PlanMergingPolicy].from[PruningPlanMergingPolicyLoggedImpl]
      make[ActivationInfo].fromValue(availableActivations)
    }

    TestEnvironment(
      bsModule = bsModule,
      appModule = appModule,
      roles = roles,
      activationInfo = availableActivations,
      activation = activation,
      memoizationRoots = testConfig.memoizationRoots.toSet,
      forcedRoots = testConfig.forcedRoots.toSet,
      parallelEnvs = testConfig.parallelEnvs,
      parallelSuites = testConfig.parallelSuites,
      parallelTests = testConfig.parallelTests,
      bootstrapFactory = BootstrapFactory.Impl,
      configBaseName = testConfig.configBaseName,
      configOverrides = testConfig.configOverrides,
      planningOptions = testConfig.planningOptions,
      testRunnerLogLevel = testConfig.testRunnerLogLevel,
    )
  }

  protected def loadRoles(@unused logger: IzLogger): RolesInfo = {
    // For all normal scenarios we don't need roles to setup a test
    RolesInfo(Set.empty, Seq.empty, Seq.empty, Seq.empty, Set.empty)
  }

  protected def makeMergeStrategy(@unused logger: IzLogger): PluginMergeStrategy = {
    SimplePluginMergeStrategy
  }

  protected def makePluginloader(@unused logger: IzLogger): PluginLoader = {
    new PluginLoaderDefaultImpl()
  }

}

object DistageTestEnv {
  private[distage] lazy val cache = new SyncCache[EnvCacheKey, TestEnvironment]

  final case class EnvCacheKey(config: TestConfig, rolesInfo: RolesInfo, mergeStrategy: PluginMergeStrategy)
}
