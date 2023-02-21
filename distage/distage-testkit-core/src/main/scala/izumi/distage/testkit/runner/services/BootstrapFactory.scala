package izumi.distage.testkit.runner.services

import distage.config.AppConfig
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.framework.services.ConfigLoader.ConfigLocation
import izumi.distage.framework.services.{ConfigLoader, ModuleProvider}
import izumi.distage.model.definition.Activation
import izumi.distage.roles.launcher.AppShutdownInitiator
import izumi.distage.roles.model.meta.RolesInfo
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.logstage.api.IzLogger
import izumi.logstage.api.logger.LogRouter
import izumi.reflect.TagK

trait BootstrapFactory {
  def makeConfigLocation(configBaseName: String): ConfigLocation
  def makeConfigLoader(configBaseName: String, logger: IzLogger): ConfigLoader
  def makeModuleProvider[F[_]: TagK](
    options: PlanningOptions,
    config: AppConfig,
    logRouter: LogRouter,
    roles: RolesInfo,
    activationInfo: ActivationInfo,
    activation: Activation,
  ): ModuleProvider
}

object BootstrapFactory {
  object Impl extends BootstrapFactory {
    override def makeConfigLocation(configBaseName: String): ConfigLocation = {
      ConfigLocation.Default
    }

    override def makeConfigLoader(configBaseName: String, logger: IzLogger): ConfigLoader = {
      new ConfigLoader.LocalFSImpl(logger, makeConfigLocation(configBaseName), ConfigLoader.Args(None, Map(configBaseName -> None)))
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
