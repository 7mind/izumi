package izumi.distage.testkit.spec

import izumi.distage.framework.model.ActivationInfo
import izumi.distage.framework.services.ActivationChoicesExtractor
import izumi.distage.model.definition.{BootstrapModuleDef, Module}
import izumi.distage.model.reflection.DIKey
import izumi.distage.plugins.load.PluginLoader
import izumi.distage.plugins.merge.PluginMergeStrategy
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.testkit.model.{TestConfig, TestEnvironment}
import izumi.fundamentals.platform.language.types.HigherKindedAny.AnyF
import izumi.reflect.TagK

trait TestEnvironmentFactory {
  def create[F[_]](
    testConfig: TestConfig,
    pluginLoader: PluginLoader,
    roles: RolesInfo,
    mergeStrategy: PluginMergeStrategy,
    tagK: TagK[F],
    defaultModule: () => Module,
  ): TestEnvironment
}

object TestEnvironmentFactory {
  final class Impl extends TestEnvironmentFactory {
    override def create[F[_]](
      testConfig: TestConfig,
      pluginLoader: PluginLoader,
      roles: RolesInfo,
      mergeStrategy: PluginMergeStrategy,
      tagK: TagK[F],
      defaultModule: () => Module,
    ): TestEnvironment = {
      val appPlugins = pluginLoader.load(testConfig.pluginConfig)
      val bsPlugins = pluginLoader.load(testConfig.bootstrapPluginConfig)
      val appModule = mergeStrategy.merge(appPlugins.result) overriddenBy testConfig.moduleOverrides
      val bootstrapModule = mergeStrategy.merge(bsPlugins.result) overriddenBy testConfig.bootstrapOverrides
      val availableActivations = new ActivationChoicesExtractor.Impl(testConfig.unusedValidAxisChoices).findAvailableChoices(appModule)
      val bsModule = bootstrapModule overriddenBy testkitBootstrapReflectiveModule(availableActivations)

      TestEnvironment(
        bsModule = bsModule,
        appModule = appModule,
        effectType = tagK.asInstanceOf[TagK[AnyF]],
        defaultModule = defaultModule(),
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
        activationStrategy = testConfig.activationStrategy,
      )(
        parallelSuites = testConfig.parallelSuites,
        parallelTests = testConfig.parallelTests,
        debugOutput = testConfig.debugOutput,
      )
    }
  }

  private[distage] def testkitBootstrapReflectiveModule(availableActivations: ActivationInfo): BootstrapModuleDef = new BootstrapModuleDef {
    //     Update `testkitBootstrapReflectiveKeys` if you add anything here
    make[ActivationInfo].fromValue(availableActivations).exposed
  }

  val testkitBootstrapReflectiveKeys: Set[DIKey] = {
    testkitBootstrapReflectiveModule(ActivationInfo(Map.empty)).keys
  }
}
