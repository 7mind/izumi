package izumi.distage.testkit.runner.impl.services

import distage.config.AppConfig
import izumi.distage.config.model.{RoleConfig, RoleConfigSource}
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.framework.services.ConfigMerger.ConfigMergerImpl
import izumi.distage.framework.services.{ConfigFilteringStrategy, ConfigLoader, ConfigLoaderArgs, ConfigLocationProvider, ModuleProvider}
import izumi.distage.model.definition.Activation
import izumi.distage.roles.DebugProperties
import izumi.distage.roles.launcher.AppShutdownInitiator
import izumi.distage.roles.model.meta.RolesInfo
import izumi.fundamentals.platform.cli.model.RoleAppArgs
import izumi.logstage.api.IzLogger
import izumi.logstage.api.logger.LogRouter
import izumi.reflect.TagK

/**
  * The purpose of this class is to allow testkit user to override
  * module loading and config loading logic by overriding [[izumi.distage.testkit.model.TestConfig.bootstrapFactory]]
  */
trait BootstrapFactory {
  def makeModuleProvider[F[_]: TagK](
    options: PlanningOptions,
    config: AppConfig,
    logRouter: LogRouter,
    roles: RolesInfo,
    activationInfo: ActivationInfo,
    activation: Activation,
  ): ModuleProvider

  def makeConfigLoader(configBaseName: String, logger: IzLogger): ConfigLoader

  protected def makeConfigLocationProvider(configBaseName: String): ConfigLocationProvider
}

object BootstrapFactory {
  object Impl extends BootstrapFactory {
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
      new ConfigLoader.LocalFSImpl(logger, merger, locationProvider, configLoaderArgs)
    }

    override def makeModuleProvider[F[_]: TagK](
      options: PlanningOptions,
      config: AppConfig,
      logRouter: LogRouter,
      roles: RolesInfo,
      activationInfo: ActivationInfo,
      activation: Activation,
    ): ModuleProvider = {
      // roles descriptor is not actually required there, we bind it just in case someone wish to inject a class depending on it
      new ModuleProvider.Impl[F](
        logRouter = logRouter,
        options = options,
        config = config,
        roles = roles,
        args = RoleAppArgs.empty,
        activationInfo = activationInfo,
        shutdownInitiator = AppShutdownInitiator.empty,
        roleAppLocator = None,
        appArtifact = None,
        setupStaticLogRouter = DebugProperties.`izumi.distage.roles.logs.static-log-router`.boolValue(true),
      )
    }
  }
}
