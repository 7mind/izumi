package izumi.distage.testkit.runner.impl.services

import distage.config.AppConfig
import izumi.distage.config.model.{GenericConfigSource, RoleConfig}
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.framework.services.ConfigMerger.ConfigMergerImpl
import izumi.distage.framework.services.{ConfigArgsProvider, ConfigLoader, ConfigLocationProvider, ModuleProvider}
import izumi.distage.model.definition.Activation
import izumi.distage.roles.launcher.AppShutdownInitiator
import izumi.distage.roles.model.meta.RolesInfo
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.logstage.api.IzLogger
import izumi.logstage.api.logger.LogRouter
import izumi.reflect.TagK

/**
  * The purpose of this class is to allow testkit user to override
  * module loading and config loading logic by overriding [[izumi.distage.testkit.model.TestConfig]]
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
      val argsProvider = ConfigArgsProvider.const(ConfigLoader.Args(None, List(RoleConfig(configBaseName, active = true, GenericConfigSource.ConfigDefault))))
      val merger = new ConfigMergerImpl(logger)
      val locationProvider = makeConfigLocationProvider(configBaseName)
      new ConfigLoader.LocalFSImpl(logger, merger, locationProvider, argsProvider)
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
        args = RawAppArgs.empty,
        activationInfo = activationInfo,
        shutdownInitiator = AppShutdownInitiator.empty,
        roleAppLocator = None,
      )
    }
  }
}
