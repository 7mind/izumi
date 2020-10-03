package izumi.distage.testkit.services.dstest

import distage.plugins.PluginLoader
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.framework.services.ActivationChoicesExtractor
import izumi.distage.model.definition.BootstrapModuleDef
import izumi.distage.plugins.load.PluginLoaderDefaultImpl
import izumi.distage.plugins.merge.{PluginMergeStrategy, SimplePluginMergeStrategy}
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.testkit.{DebugProperties, TestConfig}
import izumi.fundamentals.platform.cache.SyncCache

trait DistageTestEnv {
  private[distage] def loadEnvironment(testConfig: TestConfig): TestEnvironment = {
    val roles = loadRoles()
    val mergeStrategy = makeMergeStrategy()
    val pluginLoader = makePluginloader()
    def doMake(): TestEnvironment = {
      makeEnv(testConfig, pluginLoader, roles, mergeStrategy)
    }

    if (DebugProperties.`izumi.distage.testkit.environment.cache`.boolValue(true)) {
      DistageTestEnv.cache.getOrCompute(DistageTestEnv.EnvCacheKey(testConfig, roles, mergeStrategy), doMake())
    } else {
      doMake()
    }
  }

  private[distage] def makeEnv(
    testConfig: TestConfig,
    pluginLoader: PluginLoader,
    roles: RolesInfo,
    mergeStrategy: PluginMergeStrategy,
  ): TestEnvironment = {
    val appPlugins = pluginLoader.load(testConfig.pluginConfig)
    val bsPlugins = pluginLoader.load(testConfig.bootstrapPluginConfig)
    val appModule = mergeStrategy.merge(appPlugins) overriddenBy testConfig.moduleOverrides
    val bootstrapModule = mergeStrategy.merge(bsPlugins) overriddenBy testConfig.bootstrapOverrides
    val availableActivations = new ActivationChoicesExtractor.ActivationChoicesExtractorImpl().findAvailableChoices(appModule)

    val bsModule = bootstrapModule overriddenBy new BootstrapModuleDef {
      make[ActivationInfo].fromValue(availableActivations)
    }

    TestEnvironment(
      bsModule = bsModule,
      appModule = appModule,
      roles = roles,
      activationInfo = availableActivations,
      activation = testConfig.activation,
      memoizationRoots = testConfig.memoizationRoots,
      forcedRoots = testConfig.forcedRoots,
      parallelEnvs = testConfig.parallelEnvs,
      bootstrapFactory = testConfig.bootstrapFactory,
      configBaseName = testConfig.configBaseName,
      configOverrides = testConfig.configOverrides,
      planningOptions = testConfig.planningOptions,
      logLevel = testConfig.logLevel,
    )(
      parallelSuites = testConfig.parallelSuites,
      parallelTests = testConfig.parallelTests,
      debugOutput = testConfig.debugOutput,
    )
  }

  protected def loadRoles(): RolesInfo = {
    // For all normal scenarios we don't need roles to setup a test
    RolesInfo(Set.empty, Seq.empty, Seq.empty, Seq.empty, Set.empty)
  }

  protected def makeMergeStrategy(): PluginMergeStrategy = {
    SimplePluginMergeStrategy
  }

  protected def makePluginloader(): PluginLoader = {
    new PluginLoaderDefaultImpl()
  }

}

object DistageTestEnv {
  private[distage] val cache = new SyncCache[EnvCacheKey, TestEnvironment]

  final case class EnvCacheKey(config: TestConfig, rolesInfo: RolesInfo, mergeStrategy: PluginMergeStrategy)
}
