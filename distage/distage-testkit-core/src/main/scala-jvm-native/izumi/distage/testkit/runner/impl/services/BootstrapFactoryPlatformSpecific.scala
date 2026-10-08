package izumi.distage.testkit.runner.impl.services

import izumi.distage.config.model.{RoleConfig, RoleConfigSource}
import izumi.distage.framework.services.ConfigMerger.ConfigMergerImpl
import izumi.distage.framework.services.{ConfigFilteringStrategy, ConfigLoader, ConfigLoaderArgs, ConfigLocationProvider}
import izumi.logstage.api.IzLogger

trait BootstrapFactoryPlatformSpecific {
  protected def makeConfigLocationProvider(configBaseName: String): ConfigLocationProvider
}

private[services] trait DefaultBootstrapFactoryConfig extends BootstrapFactory {
  override protected def makeConfigLocationProvider(configBaseName: String): ConfigLocationProvider = {
    ConfigLocationProvider.Default
  }

  override def makeConfigLoader(configBaseName: String, logger: IzLogger): ConfigLoader = {
    val configLoaderArgs = ConfigLoaderArgs(global = None, configs = List(RoleConfig(configBaseName, active = true, RoleConfigSource.ConfigDefault)))
    val merger = new ConfigMergerImpl(
      logger,
      enableConfigEnvOverrides = true,
      new ConfigFilteringStrategy.Raw(
        alwaysIncludeReferenceRoleConfigs = true, // we expect no user-provided role configs in tests
        alwaysIncludeReferenceCommonConfigs = true,
        ignoreAll = false,
      ),
    )
    val locationProvider = makeConfigLocationProvider(configBaseName)
    BootstrapConfigLoader(logger, merger, locationProvider, configLoaderArgs)
  }
}
