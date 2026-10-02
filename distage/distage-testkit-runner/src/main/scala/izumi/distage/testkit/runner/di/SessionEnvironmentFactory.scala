package izumi.distage.testkit.runner.di

import izumi.distage.model.definition.Module
import izumi.distage.plugins.load.PluginLoader
import izumi.distage.plugins.merge.PluginMergeStrategy
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.testkit.model.{TestConfig, TestEnvironment}
import izumi.distage.testkit.runner.impl.services.BootstrapFactory
import izumi.distage.testkit.spec.TestEnvironmentFactory
import izumi.reflect.TagK

private[di] final class SessionEnvironmentFactory(delegate: TestEnvironmentFactory, bootstrap: BootstrapFactory) extends TestEnvironmentFactory {
  override def create[F[_]](
    config: TestConfig,
    loader: PluginLoader,
    roles: RolesInfo,
    merge: PluginMergeStrategy,
    effect: TagK[F],
    defaultModule: () => Module,
  ): TestEnvironment = {
    val owned = if (config.bootstrapFactory eq BootstrapFactory.Impl) config.copy(bootstrapFactory = bootstrap) else config
    delegate.create(owned, loader, roles, merge, effect, defaultModule)
  }
}
