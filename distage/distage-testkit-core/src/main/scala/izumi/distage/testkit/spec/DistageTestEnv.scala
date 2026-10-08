package izumi.distage.testkit.spec

import distage.plugins.PluginLoader
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.model.definition.{BootstrapModuleDef, Module}
import izumi.distage.model.reflection.DIKey
import izumi.distage.modules.DefaultModule
import izumi.distage.plugins.load.PluginLoaderDefaultImpl
import izumi.distage.plugins.merge.{PluginMergeStrategy, SimplePluginMergeStrategy}
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.testkit.DebugProperties
import izumi.distage.testkit.model.{TestConfig, TestEnvironment}
import izumi.fundamentals.platform.cache.SyncCache
import izumi.reflect.{AnyTag, TagK}

trait DistageTestEnv {
  private[distage] def loadEnvironment[F[_]](testConfig: TestConfig, tagK: TagK[F], defaultModule: DefaultModule[F]): TestEnvironment = {
    val roles = loadRoles()
    val mergeStrategy = makeMergeStrategy()
    val pluginLoader = makePluginloader()
    def doMake(): TestEnvironment = {
      makeEnv(testConfig, pluginLoader, roles, mergeStrategy, tagK, defaultModule)
    }

    if (DistageTestEnv.cache ne null) {
      DistageTestEnv.cache.getOrCompute(DistageTestEnv.EnvCacheKey(testConfig, roles, mergeStrategy, tagK), doMake())
    } else {
      doMake()
    }
  }

  private[distage] def makeEnv[F[_]](
    testConfig: TestConfig,
    pluginLoader: PluginLoader,
    roles: RolesInfo,
    mergeStrategy: PluginMergeStrategy,
    tagK: TagK[F],
    defaultModule0: DefaultModule[F],
  ): TestEnvironment = {
    new TestEnvironmentFactory.Impl().create(
      testConfig,
      pluginLoader,
      roles,
      mergeStrategy,
      tagK,
      () => {
        if (DistageTestEnv.defaultModuleCache ne null) {
          DistageTestEnv.defaultModuleCache.getOrCompute(tagK, defaultModule0.module)
        } else {
          defaultModule0.module
        }
      },
    )
  }

  protected def loadRoles(): RolesInfo = {
    // For all normal scenarios we don't need roles to setup a test
    RolesInfo(Set.empty, Set.empty, Set.empty, Set.empty, Set.empty, Set.empty)
  }

  protected def makeMergeStrategy(): PluginMergeStrategy = {
    SimplePluginMergeStrategy
  }

  protected def makePluginloader(): PluginLoader = {
    new PluginLoaderDefaultImpl()
  }

}

object DistageTestEnv {
  private[distage] final val cache: SyncCache[EnvCacheKey, TestEnvironment] = {
    if (DebugProperties.`izumi.distage.testkit.environment.cache`.boolValue(true)) {
      new SyncCache[EnvCacheKey, TestEnvironment]
    } else {
      null
    }
  }
  private[distage] final val defaultModuleCache: SyncCache[AnyTag, Module] = {
    if (DebugProperties.`izumi.distage.testkit.defaultmodule.cache`.boolValue(true)) {
      new SyncCache[AnyTag, Module]
    } else {
      null
    }
  }

  private[distage] final case class EnvCacheKey(config: TestConfig, rolesInfo: RolesInfo, mergeStrategy: PluginMergeStrategy, tag: AnyTag)

  private[distage] def testkitBootstrapReflectiveModule(availableActivations: ActivationInfo): BootstrapModuleDef = {
    TestEnvironmentFactory.testkitBootstrapReflectiveModule(availableActivations)
  }

  lazy val testkitBootstrapReflectiveKeys: Set[DIKey] = {
    TestEnvironmentFactory.testkitBootstrapReflectiveKeys
  }
}
