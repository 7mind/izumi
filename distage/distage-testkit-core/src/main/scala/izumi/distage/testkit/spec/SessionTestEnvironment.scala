package izumi.distage.testkit.spec

import izumi.distage.model.definition.Module
import izumi.distage.modules.DefaultModule
import izumi.distage.plugins.load.PluginLoader
import izumi.distage.plugins.merge.PluginMergeStrategy
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.testkit.model.{TestConfig, TestEnvironment}
import izumi.fundamentals.platform.cache.SyncCache
import izumi.reflect.{AnyTag, TagK}

final class SessionTestEnvironment(factory: TestEnvironmentFactory) {
  private val cache = new SyncCache[SessionTestEnvironment.CacheKey, TestEnvironment]

  def load[F[_]](
    testConfig: TestConfig,
    pluginLoader: PluginLoader,
    roles: RolesInfo,
    mergeStrategy: PluginMergeStrategy,
    tagK: TagK[F],
    defaultModule: DefaultModule[F],
  ): TestEnvironment = {
    val config = testConfig.copy(
      pluginConfig = testConfig.pluginConfig.snapshot(),
      bootstrapPluginConfig = testConfig.bootstrapPluginConfig.snapshot(),
    )
    val key = SessionTestEnvironment.CacheKey(
      config,
      new SessionTestEnvironment.Identity(pluginLoader),
      roles,
      new SessionTestEnvironment.Identity(mergeStrategy),
      tagK,
      defaultModule.module,
    )
    cache.getOrCompute(key, factory.create(config, pluginLoader, roles, mergeStrategy, tagK, () => defaultModule.module))
  }
}

object SessionTestEnvironment {
  private final case class CacheKey(
    config: TestConfig,
    loader: Identity[PluginLoader],
    roles: RolesInfo,
    mergeStrategy: Identity[PluginMergeStrategy],
    effect: AnyTag,
    defaultModule: Module,
  )

  private final class Identity[A <: AnyRef](val value: A) {
    override def equals(other: Any): Boolean = other match {
      case that: Identity[?] => value.eq(that.value)
      case _ => false
    }
    override def hashCode(): Int = System.identityHashCode(value)
  }
}
